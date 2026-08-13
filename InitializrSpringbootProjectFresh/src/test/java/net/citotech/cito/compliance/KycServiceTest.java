package net.citotech.cito.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import net.citotech.cito.gateway.PaymentGatewayException;
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
    void rejectsBeneficialOwnerOwnershipOutsidePolicyRange() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        KycService service = new KycService(jdbcTemplate);

        assertThatThrownBy(
                        () ->
                                service.addBeneficialOwner(
                                        10L, "Jane Owner", "NIN", "CF1234", BigDecimal.ZERO))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("ownershipPercent");
        assertThatThrownBy(
                        () ->
                                service.addBeneficialOwner(
                                        10L,
                                        "Jane Owner",
                                        "NIN",
                                        "CF1234",
                                        new BigDecimal("100.0001")))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("ownershipPercent");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void reviewOwnerApprovesAPendingRecordAndRecordsAuditEvidence() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("PENDING");
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1, 1);
        KycService service = new KycService(jdbcTemplate);

        int updated =
                service.reviewOwner(
                        7L, "APPROVED", "admin@cpay", "COMPLIANCE", "validated evidence");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate)
                .update(
                        eq(
                                "UPDATE beneficial_owners SET screening_status=:screening_status, updated_at=CURRENT_TIMESTAMP "
                                        + "WHERE id=:id AND screening_status IN ('PENDING','IN_REVIEW')"),
                        any(MapSqlParameterSource.class));
        verify(jdbcTemplate)
                .update(
                        eq(
                                "INSERT INTO kyb_review_decisions "
                                        + "(subject_type, subject_id, old_status, new_status, decision, reason, reviewer_user_id, reviewer_role) "
                                        + "VALUES (:subject_type, :subject_id, :old_status, :new_status, :decision, :reason, :reviewer_user_id, :reviewer_role)"),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void reviewOwnerRejectedKeywordMapsToRejectedStatus() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("IN_REVIEW");
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1, 1);
        KycService service = new KycService(jdbcTemplate);

        int updated = service.reviewOwner(7L, "REJECTED", "admin@cpay");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate, times(2)).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void reviewOwnerRejectsNullOrGarbageDecisionWithoutApproving() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        KycService service = new KycService(jdbcTemplate);

        assertThatThrownBy(() -> service.reviewOwner(7L, null, "admin@cpay"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("decision is required");
        assertThatThrownBy(() -> service.reviewOwner(7L, "maybe", "admin@cpay"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Unsupported KYC review decision");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void reviewOwnerRequiresReviewerAttribution() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        KycService service = new KycService(jdbcTemplate);

        assertThatThrownBy(() -> service.reviewOwner(7L, "APPROVED", " "))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("reviewedBy is required");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void reviewDocumentApprovesAndStampsVerifier() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("PENDING");
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1, 1);
        KycService service = new KycService(jdbcTemplate);

        int updated = service.reviewDocument(9L, "APPROVED", "compliance@cpay", "COMPLIANCE", null);

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate, times(2)).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void reviewDocumentRejectKeywordMapsToRejectedStatus() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("IN_REVIEW");
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1, 1);
        KycService service = new KycService(jdbcTemplate);

        int updated = service.reviewDocument(9L, "REJECT", "compliance@cpay");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate, times(2)).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void reviewDocumentRejectsNullOrGarbageDecisionWithoutApproving() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        KycService service = new KycService(jdbcTemplate);

        assertThatThrownBy(() -> service.reviewDocument(9L, null, "compliance@cpay"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("decision is required");
        assertThatThrownBy(() -> service.reviewDocument(9L, "later", "compliance@cpay"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Unsupported KYC review decision");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }
}
