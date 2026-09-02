package net.citotech.cito.treasury;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.gateway.ProviderEndpointExecutionService;
import net.citotech.cito.gateway.ProviderEndpointExecutionService.ProviderBalanceResult;
import net.citotech.cito.sharedprovider.SharedProviderAccessService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Queries a provider wallet without treating transport failure or a missing field as zero. */
@Service
public class ProviderBalanceRefreshService {
    private final NamedParameterJdbcTemplate jdbc;
    private final SharedProviderAccessService sharedProvider;
    private final ProviderEndpointExecutionService providerEndpoints;

    public ProviderBalanceRefreshService(
            NamedParameterJdbcTemplate jdbc,
            SharedProviderAccessService sharedProvider,
            ProviderEndpointExecutionService providerEndpoints) {
        this.jdbc = jdbc;
        this.sharedProvider = sharedProvider;
        this.providerEndpoints = providerEndpoints;
    }

    public Map<String, Object> refresh(long accountId) {
        Map<String, Object> account = account(accountId);
        String role = text(account.get("account_role"));
        if ("MASTER".equals(role)) {
            throw new PaymentGatewayException(
                    "Refresh a COLLECTION or DISBURSEMENT account, not the master control account");
        }
        String channel = text(account.get("channel_code"));
        String environment = text(account.get("environment"));
        String country = text(account.get("country_code"));
        String currency = text(account.get("currency_code"));
        try {
            Map<String, Object> values =
                    sharedProvider.loadActivePlatformCredential(
                            channel, environment, country, currency);
            Map<String, String> credentials = new LinkedHashMap<>();
            values.forEach(
                    (key, value) -> {
                        if (value != null) credentials.put(key, String.valueOf(value));
                    });
            ProviderBalanceResult result =
                    providerEndpoints.fetchBalance(
                            channel, role, environment, country, currency, credentials);
            if (result.available()) {
                jdbc.update(
                        "UPDATE provider_treasury_accounts SET provider_reported_balance=:balance,"
                                + " provider_balance_status='AVAILABLE',"
                                + " provider_balance_updated_at=CURRENT_TIMESTAMP(6),"
                                + " provider_balance_message=:message, lock_version=lock_version+1"
                                + " WHERE id=:id",
                        new MapSqlParameterSource()
                                .addValue("id", accountId)
                                .addValue("balance", result.balance())
                                .addValue("message", result.message()));
            } else {
                markUnavailable(accountId, result.message());
            }
        } catch (RuntimeException e) {
            markUnavailable(accountId, safe(e.getMessage()));
        }
        return status(accountId);
    }

    private void markUnavailable(long accountId, String message) {
        jdbc.update(
                "UPDATE provider_treasury_accounts SET provider_balance_status='UNAVAILABLE',"
                        + " provider_balance_message=:message, lock_version=lock_version+1 WHERE id=:id",
                new MapSqlParameterSource()
                        .addValue("id", accountId)
                        .addValue("message", safe(message)));
    }

    private Map<String, Object> account(long id) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT * FROM provider_treasury_accounts WHERE id=:id",
                        new MapSqlParameterSource("id", id));
        if (rows.isEmpty())
            throw new PaymentGatewayException("Provider treasury account not found");
        return rows.get(0);
    }

    private Map<String, Object> status(long id) {
        return jdbc.queryForMap(
                "SELECT id, provider_reported_balance AS providerReportedBalance,"
                        + " provider_balance_status AS providerBalanceStatus,"
                        + " provider_balance_updated_at AS providerBalanceUpdatedAt,"
                        + " provider_balance_message AS providerBalanceMessage"
                        + " FROM provider_treasury_accounts WHERE id=:id",
                new MapSqlParameterSource("id", id));
    }

    private String safe(String value) {
        String message =
                value == null || value.isBlank() ? "Provider balance is unavailable" : value;
        return message.substring(0, Math.min(500, message.length()));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
