package net.citotech.cito.compliance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ComplianceCaseServiceTest {

    @Test
    void reviewRiskDecisionsCreateShadowScoreAndOpenCase() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ComplianceCaseService service = new ComplianceCaseService(jdbcTemplate);

        service.captureRiskDecision(
            42L,
            "PAY-001",
            "PAYOUT",
            new BigDecimal("1500000"),
            "UGX",
            RiskDecision.review("SINGLE_TRANSACTION_CAP", "Amount exceeds review threshold"));

        verify(jdbcTemplate, times(2)).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void closingCaseAddsDecisionNote() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        ComplianceCaseService service = new ComplianceCaseService(jdbcTemplate);

        service.decideCase(9L, "APPROVED", "Documents verified", "ops@example.com");

        verify(jdbcTemplate, times(2)).update(anyString(), any(MapSqlParameterSource.class));
    }
}
