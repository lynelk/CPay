package net.citotech.cito.communication.routing;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.communication.sms.SmsGatewayAdapter;
import net.citotech.cito.communication.sms.SmsSendRequest;
import net.citotech.cito.communication.sms.SmsSendResult;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Routes each logical SMS to the adapter selected by {@code communication_routing_rules} (ISO
 * domain mapping: communication/routing, track B1a). It is the {@link Primary} {@link
 * SmsGatewayAdapter} bean, so the delivery worker ({@code SmsDeliveryService}), the opt-in
 * scheduler and the admin trigger all go through rule-based lookup without any code change.
 *
 * <p>Rule precedence (single query, in order): enabled rules only; a merchant-specific rule
 * (merchant_id = the request's merchant) beats the platform default (merchant_id IS NULL); lowest
 * {@code priority} wins; ties break by lowest id. The V50 seed routes SMS to {@code
 * LEGACY_SETTINGS} as the platform default, so an unconfigured deployment keeps the exact
 * pre-router behavior. Any unresolved/unknown/disabled target falls back to the legacy adapter, and
 * a routing-table read failure (e.g. DB briefly down) also falls back to legacy rather than
 * hard-failing the batch — routing is metadata, delivery availability wins.
 */
@Component
@Primary
public class ProviderRouter implements SmsGatewayAdapter {

    private static final Logger logger = Logger.getLogger(ProviderRouter.class.getName());

    private static final String DEFAULT_CHANNEL = "SMS";
    private static final String LEGACY_CODE = "LEGACY_SETTINGS";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Map<String, SmsGatewayAdapter> adaptersByCode;

    public ProviderRouter(
            NamedParameterJdbcTemplate jdbcTemplate,
            Map<String, SmsGatewayAdapter> smsAdaptersByCode) {
        this.jdbcTemplate = jdbcTemplate;
        this.adaptersByCode = Map.copyOf(smsAdaptersByCode);
    }

    @Override
    public SmsSendResult send(SmsSendRequest request) {
        return resolveFor(request.merchantId(), DEFAULT_CHANNEL).send(request);
    }

    /**
     * Resolves the adapter for a merchant + channel. Returns the legacy adapter on any fallback so
     * callers never receive null.
     */
    SmsGatewayAdapter resolveFor(long merchantId, String channel) {
        SmsGatewayAdapter legacy = adaptersByCode.get(LEGACY_CODE);
        if (legacy == null) {
            throw new IllegalStateException(
                    "No legacy SMS adapter registered in smsAdaptersByCode");
        }

        String providerCode;
        try {
            providerCode = resolveProviderCode(merchantId, channel);
        } catch (Exception ex) {
            logger.log(
                    Level.WARNING,
                    "SMS routing lookup failed (merchant "
                            + merchantId
                            + "); falling back to legacy adapter: "
                            + ex.getMessage());
            return legacy;
        }

        SmsGatewayAdapter selected = providerCode == null ? null : adaptersByCode.get(providerCode);
        if (selected == null) {
            if (providerCode != null) {
                logger.log(
                        Level.WARNING,
                        "Routing rule selected unknown/unregistered provider \""
                                + providerCode
                                + "\"; falling back to legacy adapter");
            }
            return legacy;
        }
        return selected;
    }

    private String resolveProviderCode(long merchantId, String channel) {
        List<String> codes =
                jdbcTemplate.query(
                        "SELECT r.provider_code FROM communication_routing_rules r "
                                + "JOIN communication_providers p "
                                + "  ON p.provider_code = r.provider_code AND p.channel = r.channel "
                                + "WHERE r.channel = :channel "
                                + "  AND r.enabled_flag = 'YES' "
                                + "  AND p.enabled_flag = 'YES' "
                                + "  AND (r.merchant_id = :merchantId OR r.merchant_id IS NULL) "
                                + "ORDER BY (r.merchant_id = :merchantId) DESC, r.priority ASC, r.id ASC "
                                + "LIMIT 1",
                        new MapSqlParameterSource()
                                .addValue("channel", channel)
                                .addValue("merchantId", merchantId),
                        (rs, rowNum) -> rs.getString("provider_code"));
        return codes.isEmpty() ? null : codes.get(0);
    }
}
