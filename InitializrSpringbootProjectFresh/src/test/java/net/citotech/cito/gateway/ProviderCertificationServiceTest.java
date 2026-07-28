package net.citotech.cito.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ProviderCertificationServiceTest {

    @Test
    void sandboxRunIsRecordedAsCertificationEvidence() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ProviderCertificationService service = new ProviderCertificationService(jdbcTemplate);

        service.recordSandboxEvidence("MTN", "MTN_MOMO", "collect_success", 77L, "PASSED", "ok");

        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void evidenceCanBeApproved() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        ProviderCertificationService service = new ProviderCertificationService(jdbcTemplate);

        int updated = service.approveEvidence(77L, "ops@example.com");

        assertThat(updated).isEqualTo(1);
    }

    @Test
    void approvalOnlyTransitionsFromCapturedEvidence() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        ProviderCertificationService service = new ProviderCertificationService(jdbcTemplate);

        service.approveEvidence(77L, "ops@example.com");

        verify(jdbcTemplate).update(contains("evidence_status='CAPTURED'"), any(MapSqlParameterSource.class));
    }
}
