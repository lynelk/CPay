package net.citotech.cito.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class KycServiceTest {

    @Test
    void recordsBeneficialOwnerAndReturnsGeneratedId() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        eq("SELECT LAST_INSERT_ID()"),
                        any(MapSqlParameterSource.class),
                        eq(Long.class)))
                .thenReturn(42L);
        KycService service = new KycService(jdbcTemplate);

        long id =
                service.addBeneficialOwner(
                        10L, "Jane Owner", "NIN", "CF1234", new BigDecimal("51.0"));

        assertThat(id).isEqualTo(42L);
        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void reviewOwnerApprovesAPendingRecord() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        KycService service = new KycService(jdbcTemplate);

        int updated = service.reviewOwner(7L, "APPROVED", "admin@cpay");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void reviewOwnerRejectedKeywordMapsToRejectedStatus() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        KycService service = new KycService(jdbcTemplate);

        int updated = service.reviewOwner(7L, "REJECTED", "admin@cpay");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void reviewDocumentApprovesAndStampsVerifier() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        KycService service = new KycService(jdbcTemplate);

        int updated = service.reviewDocument(9L, "APPROVED", "compliance@cpay");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void reviewDocumentRejectKeywordMapsToRejectedStatus() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        KycService service = new KycService(jdbcTemplate);

        int updated = service.reviewDocument(9L, "REJECT", "compliance@cpay");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }
}
