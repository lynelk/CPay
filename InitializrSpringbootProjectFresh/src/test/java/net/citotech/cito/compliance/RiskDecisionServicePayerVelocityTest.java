package net.citotech.cito.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.PaymentPartyRequest;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Payer-velocity cap coverage: a COLLECT request whose payer exceeds the configured {@code
 * PAYER_VELOCITY} threshold_count returns a REVIEW decision (the seeded global rule is REVIEW)
 * instead of proceeding to the daily cap. Non-COLLECT requests skip the check entirely.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class RiskDecisionServicePayerVelocityTest {

    private static final int VELOCITY_THRESHOLD = 20;

    @Test
    void flagsPayerVelocityBreachAsReviewBeforeTheDailyCap() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ComplianceCaseService complianceCaseService = mock(ComplianceCaseService.class);
        SanctionsScreeningService sanctionsScreeningService = mock(SanctionsScreeningService.class);
        when(sanctionsScreeningService.screenPayment(any(), any(), anyString()))
                .thenReturn(RiskDecision.allow("ok"));
        // First Integer query = blocklist count (0 = not blocked); second = payer velocity count.
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0, VELOCITY_THRESHOLD);
        stubRuleLookup(jdbcTemplate);

        RiskDecisionService service =
                new RiskDecisionService(
                        jdbcTemplate, complianceCaseService, sanctionsScreeningService);

        RiskDecision decision = service.authorizePayment(merchant(), collectRequest(), "COLLECT");

        assertThat(decision.getDecision()).isEqualTo("REVIEW");
        assertThat(decision.getReasonCode()).isEqualTo("PAYER_VELOCITY");
    }

    @Test
    void skipsPayerVelocityForPayoutRequests() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ComplianceCaseService complianceCaseService = mock(ComplianceCaseService.class);
        SanctionsScreeningService sanctionsScreeningService = mock(SanctionsScreeningService.class);
        when(sanctionsScreeningService.screenPayment(any(), any(), anyString()))
                .thenReturn(RiskDecision.allow("ok"));
        // Blocklist count only; no velocity query should ever run for a payout.
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);

        RiskDecisionService service =
                new RiskDecisionService(
                        jdbcTemplate, complianceCaseService, sanctionsScreeningService);

        RiskDecision decision = service.authorizePayment(merchant(), payoutRequest(), "PAYOUT");

        assertThat(decision.getDecision()).isEqualTo("ALLOW");
    }

    /**
     * Routes the findRule RowMapper queries by the bound rule_type parameter: the PAYER_VELOCITY
     * lookup returns a rule with threshold_count=20 and decision REVIEW; every other {@code query}
     * invocation (including the kycTier lookup, which binds entity_type/profile_type instead of
     * rule_type) returns no row so the single/daily amount caps allow.
     */
    private void stubRuleLookup(NamedParameterJdbcTemplate jdbcTemplate) {
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            MapSqlParameterSource params = invocation.getArgument(1);
                            if (!params.hasValue("rule_type")
                                    || !"PAYER_VELOCITY".equals(params.getValue("rule_type"))) {
                                return List.of();
                            }
                            ResultSet row = mock(ResultSet.class);
                            when(row.getString("decision")).thenReturn("REVIEW");
                            when(row.getBigDecimal("threshold_amount"))
                                    .thenReturn(new BigDecimal("0"));
                            when(row.getObject("threshold_count")).thenReturn(VELOCITY_THRESHOLD);
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(row, 0));
                        });
    }

    private Merchant merchant() {
        Merchant merchant = new Merchant();
        merchant.setId(17L);
        merchant.setAccount_number("M100");
        merchant.setStatus("ACTIVE");
        return merchant;
    }

    private PaymentRequest collectRequest() {
        PaymentPartyRequest payer = new PaymentPartyRequest();
        payer.setType("MSISDN");
        payer.setValue("256770000000");
        PaymentRequest request = new PaymentRequest();
        request.setMerchantNumber("M100");
        request.setAmount("10000");
        request.setCurrency("UGX");
        request.setCountry("UG");
        request.setReference("VELOCITY-1");
        request.setPayer(payer);
        return request;
    }

    private PaymentRequest payoutRequest() {
        PaymentPartyRequest payee = new PaymentPartyRequest();
        payee.setType("MSISDN");
        payee.setValue("256700000001");
        PaymentRequest request = new PaymentRequest();
        request.setMerchantNumber("M100");
        request.setAmount("10000");
        request.setCurrency("UGX");
        request.setCountry("UG");
        request.setReference("VELOCITY-2");
        request.setPayee(payee);
        return request;
    }
}
