package net.citotech.cito.api.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.StatementExportResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Covers audit D4: the statement export endpoint's flat {@code limit} silently truncated large date
 * ranges with no way for a client to fetch the remainder. This verifies the added keyset cursor - a
 * page is trimmed to the requested limit, a next-page cursor is only returned when a further row
 * exists, and a malformed cursor is rejected rather than silently ignored or crashing.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class MerchantStatementExportServiceTest {

    @Test
    void firstPageTrimsToLimitAndReturnsANextCursorWhenMoreRowsExist() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantReadAuditService auditService = mock(MerchantReadAuditService.class);
        Timestamp t3 = new Timestamp(3_000_000L);
        Timestamp t2 = new Timestamp(2_000_000L);
        Timestamp t1 = new Timestamp(1_000_000L);
        stubRows(
                jdbcTemplate,
                List.of(new FakeRow(30, t3), new FakeRow(20, t2), new FakeRow(10, t1)));

        MerchantStatementExportService service =
                new MerchantStatementExportService(jdbcTemplate, auditService);
        Merchant merchant = statementCapableMerchant();

        StatementExportResponse response =
                service.export(merchant, "1000003", "2026-01-01", "2026-01-31", 2, null);

        assertThat(response.getRows())
                .extracting(StatementExportResponse.StatementRow::getId)
                .containsExactly(30L, 20L);
        assertThat(response.getNextCursor()).isNotNull();
        assertThat(decodeCursor(response.getNextCursor())).isEqualTo(t2.getTime() + ":" + 20);
    }

    @Test
    void lastPageOmitsTheNextCursorWhenRowCountIsAtOrBelowLimit() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantReadAuditService auditService = mock(MerchantReadAuditService.class);
        stubRows(
                jdbcTemplate,
                List.of(
                        new FakeRow(20, new Timestamp(2_000_000L)),
                        new FakeRow(10, new Timestamp(1_000_000L))));

        MerchantStatementExportService service =
                new MerchantStatementExportService(jdbcTemplate, auditService);
        Merchant merchant = statementCapableMerchant();

        StatementExportResponse response =
                service.export(merchant, "1000003", "2026-01-01", "2026-01-31", 2, null);

        assertThat(response.getRows()).hasSize(2);
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void rejectsAMalformedCursorInsteadOfSilentlyIgnoringOrCrashing() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantReadAuditService auditService = mock(MerchantReadAuditService.class);
        MerchantStatementExportService service =
                new MerchantStatementExportService(jdbcTemplate, auditService);
        Merchant merchant = statementCapableMerchant();

        assertThatThrownBy(
                        () ->
                                service.export(
                                        merchant,
                                        "1000003",
                                        "2026-01-01",
                                        "2026-01-31",
                                        2,
                                        "%%not-a-cursor%%"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("cursor");
    }

    @Test
    void resumingFromACursorFiltersStrictlyBeforeItInTheSqlParameters() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantReadAuditService auditService = mock(MerchantReadAuditService.class);
        stubRows(jdbcTemplate, List.of(new FakeRow(10, new Timestamp(1_000_000L))));

        MerchantStatementExportService service =
                new MerchantStatementExportService(jdbcTemplate, auditService);
        Merchant merchant = statementCapableMerchant();
        String cursor = encodeCursor(2_000_000L, 20L);

        service.export(merchant, "1000003", "2026-01-01", "2026-01-31", 2, cursor);

        ArgumentCaptor<SqlParameterSource> captor =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(anyString(), captor.capture(), any(RowMapper.class));
        assertThat(captor.getValue().getValue("cursor_created_on"))
                .isEqualTo(new Timestamp(2_000_000L));
        assertThat(captor.getValue().getValue("cursor_id")).isEqualTo(20L);
    }

    @Test
    void exportForPortalWorksWithoutTheStatementExportApiPrivilege() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantReadAuditService auditService = mock(MerchantReadAuditService.class);
        stubRows(jdbcTemplate, List.of(new FakeRow(10, new Timestamp(1_000_000L))));

        MerchantStatementExportService service =
                new MerchantStatementExportService(jdbcTemplate, auditService);
        Merchant merchant = new Merchant();
        merchant.setId(7L);
        merchant.setAccount_number("1000003");
        merchant.setStatus("ACTIVE");
        merchant.setAllowed_apis(new String[0]);

        StatementExportResponse response =
                service.exportForPortal(merchant, "2026-01-01", "2026-01-31", 2, null);

        assertThat(response.getRows()).hasSize(1);
        assertThat(response.getMerchantNumber()).isEqualTo("1000003");
    }

    @Test
    void exportForPortalStillRejectsANonActiveMerchant() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantReadAuditService auditService = mock(MerchantReadAuditService.class);
        MerchantStatementExportService service =
                new MerchantStatementExportService(jdbcTemplate, auditService);
        Merchant merchant = new Merchant();
        merchant.setId(7L);
        merchant.setAccount_number("1000003");
        merchant.setStatus("SUSPENDED");

        assertThatThrownBy(
                        () ->
                                service.exportForPortal(
                                        merchant, "2026-01-01", "2026-01-31", 2, null))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void
            exportForAdminResolvesTheMerchantByAccountNumberAndWorksWithoutTheStatementExportApiPrivilege() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantReadAuditService auditService = mock(MerchantReadAuditService.class);
        stubMerchantLookup(jdbcTemplate, "1000003", "ACTIVE", new String[0]);
        stubStatementRows(jdbcTemplate, List.of(new FakeRow(10, new Timestamp(1_000_000L))));

        MerchantStatementExportService service =
                new MerchantStatementExportService(jdbcTemplate, auditService);

        StatementExportResponse response =
                service.exportForAdmin("1000003", "2026-01-01", "2026-01-31", 2, null);

        assertThat(response.getRows()).hasSize(1);
        assertThat(response.getMerchantNumber()).isEqualTo("1000003");
    }

    @Test
    void exportForAdminWorksForANonActiveMerchantSoOpsCanInvestigateASuspendedAccount() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantReadAuditService auditService = mock(MerchantReadAuditService.class);
        stubMerchantLookup(jdbcTemplate, "1000003", "SUSPENDED", new String[0]);
        stubStatementRows(jdbcTemplate, List.of(new FakeRow(10, new Timestamp(1_000_000L))));

        MerchantStatementExportService service =
                new MerchantStatementExportService(jdbcTemplate, auditService);

        StatementExportResponse response =
                service.exportForAdmin("1000003", "2026-01-01", "2026-01-31", 2, null);

        assertThat(response.getRows()).hasSize(1);
    }

    @Test
    void exportForAdminRejectsAnUnknownMerchantNumber() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantReadAuditService auditService = mock(MerchantReadAuditService.class);
        stubNoMerchantFound(jdbcTemplate);

        MerchantStatementExportService service =
                new MerchantStatementExportService(jdbcTemplate, auditService);

        assertThatThrownBy(
                        () ->
                                service.exportForAdmin(
                                        "does-not-exist", "2026-01-01", "2026-01-31", 2, null))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not found");
    }

    /**
     * Stubs {@code Common.getMerchantByAccountNumber}'s underlying query (a plain {@code SELECT *
     * FROM merchants WHERE account_number=:account_number}, no merchant-user join query needed
     * since the merchant has no users here) alongside the statement rows query on the same shared
     * mock template - {@code exportForAdmin} issues both against the same {@link
     * NamedParameterJdbcTemplate}, so this differentiates by matching on the SQL text.
     */
    private void stubMerchantLookup(
            NamedParameterJdbcTemplate jdbcTemplate,
            String accountNumber,
            String status,
            String[] allowedApis) {
        when(jdbcTemplate.query(
                        contains("FROM merchants"),
                        any(SqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper rm = invocation.getArgument(2);
                            ResultSet rs = mock(ResultSet.class);
                            when(rs.getString("name")).thenReturn("Test Merchant");
                            when(rs.getString("account_number")).thenReturn(accountNumber);
                            when(rs.getString("status")).thenReturn(status);
                            when(rs.getLong("id")).thenReturn(7L);
                            when(rs.getString("created_on")).thenReturn("2026-01-01");
                            when(rs.getString("created_by")).thenReturn("system");
                            when(rs.getString("account_type")).thenReturn("MERCHANT");
                            when(rs.getString("public_key")).thenReturn(null);
                            when(rs.getString("private_key")).thenReturn(null);
                            when(rs.getString("hmac_secret")).thenReturn(null);
                            when(rs.getString("short_name")).thenReturn("Test");
                            when(rs.getString("allowed_apis"))
                                    .thenReturn(
                                            allowedApis.length == 0
                                                    ? ""
                                                    : String.join(",", allowedApis));
                            return List.of(rm.mapRow(rs, 1));
                        });
    }

    private void stubNoMerchantFound(NamedParameterJdbcTemplate jdbcTemplate) {
        when(jdbcTemplate.query(
                        contains("FROM merchants"),
                        any(SqlParameterSource.class),
                        any(RowMapper.class)))
                .thenReturn(List.of());
    }

    private void stubStatementRows(NamedParameterJdbcTemplate jdbcTemplate, List<FakeRow> data) {
        when(jdbcTemplate.query(
                        contains("merchant_statement"),
                        any(SqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper rm = invocation.getArgument(2);
                            List<Object> mapped = new ArrayList<>();
                            int rowNum = 1;
                            for (FakeRow fr : data) {
                                ResultSet rs = mock(ResultSet.class);
                                stubResultSetRow(rs, fr);
                                mapped.add(rm.mapRow(rs, rowNum++));
                            }
                            return mapped;
                        });
    }

    private record FakeRow(long id, Timestamp createdOn) {}

    private void stubRows(NamedParameterJdbcTemplate jdbcTemplate, List<FakeRow> data) {
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper rm = invocation.getArgument(2);
                            List<Object> mapped = new ArrayList<>();
                            int rowNum = 1;
                            for (FakeRow fr : data) {
                                ResultSet rs = mock(ResultSet.class);
                                stubResultSetRow(rs, fr);
                                mapped.add(rm.mapRow(rs, rowNum++));
                            }
                            return mapped;
                        });
    }

    private void stubResultSetRow(ResultSet rs, FakeRow fr) throws SQLException {
        when(rs.getLong("id")).thenReturn(fr.id());
        when(rs.getTimestamp("created_on")).thenReturn(fr.createdOn());
        when(rs.getString("created_on")).thenReturn(fr.createdOn().toString());
        when(rs.getString("gateway_id")).thenReturn("MTN_MOMO");
        when(rs.getString("tx_type")).thenReturn("CR");
        when(rs.getString("description")).thenReturn("");
        when(rs.getString("narrative")).thenReturn("");
        when(rs.getBigDecimal("amount")).thenReturn(BigDecimal.TEN);
        when(rs.getString("currency")).thenReturn("UGX");
        when(rs.getBigDecimal("mtnmm_balance")).thenReturn(BigDecimal.ZERO);
        when(rs.getBigDecimal("airtelmm_balance")).thenReturn(BigDecimal.ZERO);
        when(rs.getBigDecimal("safaricom_balance")).thenReturn(BigDecimal.ZERO);
        when(rs.getBigDecimal("sms_balance")).thenReturn(BigDecimal.ZERO);
        when(rs.getString("tx_merchant_ref")).thenReturn("ref-" + fr.id());
        when(rs.getString("tx_unique_id")).thenReturn("uid-" + fr.id());
        when(rs.getString("status")).thenReturn("SUCCESSFUL");
    }

    private Merchant statementCapableMerchant() {
        Merchant merchant = new Merchant();
        merchant.setId(7L);
        merchant.setAccount_number("1000003");
        merchant.setStatus("ACTIVE");
        merchant.setAllowed_apis(new String[] {Common.API_STATEMENT_EXPORT});
        return merchant;
    }

    private String encodeCursor(long epochMillis, long id) {
        String raw = epochMillis + ":" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeCursor(String cursor) {
        return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    }
}
