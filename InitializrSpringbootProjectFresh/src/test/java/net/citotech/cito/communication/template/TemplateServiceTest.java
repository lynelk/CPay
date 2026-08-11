package net.citotech.cito.communication.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.citotech.cito.communication.template.TemplateService.RenderedTemplate;
import net.citotech.cito.communication.template.TemplateService.TemplateRow;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers the V51 template catalog: placeholder rendering (both subject and body), the literal
 * empty-string gap for a missing context value, and the fail-closed behavior for unknown or
 * INACTIVE templates.
 */
class TemplateServiceTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private TemplateService service;

    private Function<String, List<TemplateRow>> templateLookup = sql -> List.of();

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            String sql = invocation.getArgument(0);
                            if (sql.contains("communication_message_templates")) {
                                return templateLookup.apply(sql);
                            }
                            return List.of();
                        });
        service = new TemplateService(jdbcTemplate);
    }

    @Test
    void rendersSubjectAndBodyFromContext() {
        templateLookup =
                sql ->
                        List.of(
                                new TemplateRow(
                                        1L,
                                        "merchant_sms_payment_receipt",
                                        "SMS",
                                        null,
                                        "Payment of {amount} received. Ref {reference}.",
                                        "ACTIVE",
                                        "2026-01-01",
                                        "2026-01-01"));

        RenderedTemplate rendered =
                service.render(
                        "merchant_sms_payment_receipt",
                        "SMS",
                        Map.of("amount", "UGX 5,000", "reference", "TX-123"));

        assertThat(rendered.body()).isEqualTo("Payment of UGX 5,000 received. Ref TX-123.");
        assertThat(rendered.subject()).isNull();
    }

    @Test
    void missingContextValueRendersAsLiteralEmptyGap() {
        templateLookup =
                sql ->
                        List.of(
                                new TemplateRow(
                                        1L,
                                        "tpl",
                                        "EMAIL",
                                        "Hi {name}",
                                        "Amount {amount}",
                                        "ACTIVE",
                                        "2026-01-01",
                                        "2026-01-01"));

        RenderedTemplate rendered = service.render("tpl", "EMAIL", Map.of());

        assertThat(rendered.subject()).isEqualTo("Hi ");
        assertThat(rendered.body()).isEqualTo("Amount ");
    }

    @Test
    void unknownTemplateThrowsInsteadOfFallingBack() {
        templateLookup = sql -> List.of();

        assertThatThrownBy(() -> service.render("missing", "SMS", Map.of()))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Unknown message template missing");
    }

    @Test
    void inactiveTemplateThrows() {
        templateLookup =
                sql ->
                        List.of(
                                new TemplateRow(
                                        2L,
                                        "deprecated",
                                        "SMS",
                                        null,
                                        "old",
                                        "INACTIVE",
                                        "2026-01-01",
                                        "2026-01-01"));

        assertThatThrownBy(() -> service.render("deprecated", "SMS", Map.of()))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void saveRejectsBlankBody() {
        assertThatThrownBy(() -> service.save("k", "SMS", null, "  "))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("bodyTemplate is required");
    }
}
