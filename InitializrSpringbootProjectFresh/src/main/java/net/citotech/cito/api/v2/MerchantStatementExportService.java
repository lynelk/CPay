package net.citotech.cito.api.v2;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.StatementExportResponse;
import net.citotech.cito.api.v2.dto.StatementExportResponse.StatementRow;
import net.citotech.cito.export.ExportColumn;
import net.citotech.cito.export.TabularExportService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MerchantStatementExportService {
    private static final int DEFAULT_LIMIT = 1000;
    private static final int MAX_LIMIT = 5000;

    /**
     * Column layout shared by the CSV and XLSX export paths (audit M5) - declared once here so both
     * formats stay in sync instead of each hand-rolling its own column list.
     */
    private static final List<ExportColumn<StatementRow>> EXPORT_COLUMNS =
            List.of(
                    ExportColumn.of("id", StatementRow::getId),
                    ExportColumn.of("created_on", StatementRow::getCreatedOn),
                    ExportColumn.of("gateway_id", StatementRow::getGatewayId),
                    ExportColumn.of("transaction_type", StatementRow::getTransactionType),
                    ExportColumn.of("amount", StatementRow::getAmount),
                    ExportColumn.of("currency", StatementRow::getCurrency),
                    ExportColumn.of("merchant_reference", StatementRow::getMerchantReference),
                    ExportColumn.of("transaction_id", StatementRow::getTransactionId),
                    ExportColumn.of("status", StatementRow::getTransactionStatus),
                    ExportColumn.of("description", StatementRow::getDescription),
                    ExportColumn.of("narrative", StatementRow::getNarrative),
                    ExportColumn.of("mtn_balance", StatementRow::getMtnBalance),
                    ExportColumn.of("airtel_balance", StatementRow::getAirtelBalance),
                    ExportColumn.of("safaricom_balance", StatementRow::getSafaricomBalance),
                    ExportColumn.of("sms_balance", StatementRow::getSmsBalance));

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MerchantReadAuditService auditService;
    private final TabularExportService tabularExportService;

    public MerchantStatementExportService(
            NamedParameterJdbcTemplate jdbcTemplate, MerchantReadAuditService auditService) {
        this(jdbcTemplate, auditService, new TabularExportService());
    }

    @Autowired
    public MerchantStatementExportService(
            NamedParameterJdbcTemplate jdbcTemplate,
            MerchantReadAuditService auditService,
            TabularExportService tabularExportService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.tabularExportService = tabularExportService;
    }

    public StatementExportResponse export(
            Merchant merchant,
            String merchantNumber,
            String startDate,
            String endDate,
            Integer limit) {
        return export(merchant, merchantNumber, startDate, endDate, limit, null);
    }

    /**
     * Cursor-paginated statement export (audit D4). A flat {@code limit} silently truncated large
     * date ranges with no way for a client to fetch the rest; this adds an opaque keyset cursor
     * (last row's created_on + id) over the existing {@code ORDER BY created_on DESC, id DESC} so
     * pages are stable even as new statement rows are inserted concurrently - unlike offset/page-
     * number pagination, which can skip or repeat rows under concurrent writes.
     */
    public StatementExportResponse export(
            Merchant merchant,
            String merchantNumber,
            String startDate,
            String endDate,
            Integer limit,
            String cursor) {
        validateMerchant(merchant, merchantNumber);
        return buildExport(merchant, merchantNumber, startDate, endDate, limit, cursor);
    }

    /**
     * Self-service statement export for a merchant portal user (audit N3), reached via a
     * session-authenticated endpoint rather than a v2-signed API call. Skips the allowed_apis check
     * that {@link #export} enforces: allowed_apis governs what an external integration key may call
     * against the signed v2 API, not what a merchant's own logged-in staff can see of their own
     * data in the portal - those are different trust boundaries. Still requires the merchant to be
     * ACTIVE.
     */
    public StatementExportResponse exportForPortal(
            Merchant merchant, String startDate, String endDate, Integer limit, String cursor) {
        validateActiveMerchant(merchant);
        return buildExport(
                merchant, merchant.getAccount_number(), startDate, endDate, limit, cursor);
    }

    /**
     * Admin statement export (audit M5): backs the admin portal's "Account Statement" screen,
     * which previously built an Excel file client-side from whatever rows already happened to be
     * loaded in the table instead of asking the server for the full requested range. The caller is
     * an already-authenticated admin/ops operator (cookie-session admin portal, gated by {@code
     * @PreAuthorize("hasRole('ADMIN')")} on the controller plus the {@code /api/v2/admin/**} ->
     * ADMIN filter-chain rule) rather than an external v2-signed API integration key or a merchant's
     * own logged-in staff, so - unlike {@link #export} - there is no pre-verified {@link Merchant}
     * to reuse; this resolves it server-side from {@code merchantNumber}. Like {@link
     * #exportForPortal}, it skips the {@code allowed_apis} check {@link #export} enforces: that
     * governs what an external integration key may call against the signed v2 API, not what an
     * authenticated admin operator may view of a merchant's own statement - different trust
     * boundary entirely. Unlike {@link #exportForPortal}, it does not require the merchant to be
     * ACTIVE: ops staff legitimately need to pull a statement while investigating a suspended or
     * closed merchant, not only a healthy one. Still read-only and still audited via the same
     * {@code auditService.record(...)} call inside {@link #buildExport} that every other export
     * path goes through.
     */
    public StatementExportResponse exportForAdmin(
            String merchantNumber, String startDate, String endDate, Integer limit, String cursor) {
        Merchant merchant = Common.getMerchantByAccountNumber(merchantNumber, jdbcTemplate);
        if (merchant == null) {
            throw new PaymentGatewayException("Merchant not found: " + merchantNumber);
        }
        return buildExport(merchant, merchantNumber, startDate, endDate, limit, cursor);
    }

    private StatementExportResponse buildExport(
            Merchant merchant,
            String merchantNumber,
            String startDate,
            String endDate,
            Integer limit,
            String cursor) {
        LocalDate start = parseDate(startDate, "startDate");
        LocalDate end = parseDate(endDate, "endDate");
        if (end.isBefore(start)) {
            throw new PaymentGatewayException("endDate must be on or after startDate");
        }
        int boundedLimit = boundLimit(limit);
        Cursor decodedCursor = decodeCursor(cursor);

        String sql =
                "SELECT ms.id, ms.created_on, ms.gateway_id, ms.tx_type, ms.description, ms.narrative, "
                        + "ms.amount, ms.currency, ms.mtnmm_balance, ms.airtelmm_balance, ms.safaricom_balance, ms.sms_balance, "
                        + "tx.tx_merchant_ref, tx.tx_unique_id, tx.status "
                        + "FROM "
                        + Common.DB_TABLE_MERCHANT_STATEMENT
                        + " ms "
                        + "LEFT JOIN "
                        + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                        + " tx ON tx.id = ms.transactions_log_id "
                        + "WHERE ms.merchant_id = :merchant_id "
                        + "AND ms.created_on >= :start_at "
                        + "AND ms.created_on < :end_at "
                        + (decodedCursor != null
                                ? "AND (ms.created_on < :cursor_created_on "
                                        + "OR (ms.created_on = :cursor_created_on AND ms.id < :cursor_id)) "
                                : "")
                        + "ORDER BY ms.created_on DESC, ms.id DESC "
                        + "LIMIT :limit";

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchant.getId());
        p.addValue("start_at", Timestamp.valueOf(start.atStartOfDay()));
        p.addValue("end_at", Timestamp.valueOf(end.plusDays(1).atStartOfDay()));
        // Fetch one extra row so we can tell whether another page follows without a second query.
        p.addValue("limit", boundedLimit + 1);
        if (decodedCursor != null) {
            p.addValue("cursor_created_on", decodedCursor.createdOn());
            p.addValue("cursor_id", decodedCursor.id());
        }

        List<CursorRow> fetched = jdbcTemplate.query(sql, p, rowMapper());
        boolean hasMore = fetched.size() > boundedLimit;
        List<CursorRow> page = hasMore ? fetched.subList(0, boundedLimit) : fetched;

        List<StatementRow> rows = new ArrayList<>(page.size());
        for (CursorRow row : page) {
            rows.add(row.row());
        }
        auditService.record(
                merchant, "STATEMENT_EXPORT_READ", start + " to " + end + ", rows=" + rows.size());

        StatementExportResponse response = new StatementExportResponse();
        response.setMerchantNumber(merchantNumber);
        response.setStartDate(start.toString());
        response.setEndDate(end.toString());
        response.setCount(rows.size());
        response.setRows(rows);
        if (hasMore) {
            CursorRow last = page.get(page.size() - 1);
            response.setNextCursor(encodeCursor(last.createdOn(), last.row().getId()));
        }
        return response;
    }

    private record Cursor(Timestamp createdOn, long id) {}

    private record CursorRow(StatementRow row, Timestamp createdOn) {}

    private String encodeCursor(Timestamp createdOn, long id) {
        String raw = createdOn.getTime() + ":" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.indexOf(':');
            long epochMillis = Long.parseLong(raw.substring(0, sep));
            long id = Long.parseLong(raw.substring(sep + 1));
            return new Cursor(new Timestamp(epochMillis), id);
        } catch (RuntimeException ex) {
            throw new PaymentGatewayException("cursor is invalid");
        }
    }

    /**
     * CSV rendering of a statement export page (audit M5: routed through the shared {@link
     * TabularExportService} instead of hand-building the CSV string here, so this stays in lockstep
     * with the XLSX rendering below and with every other export surface that adopts the same
     * helper).
     */
    public String toCsv(StatementExportResponse response) {
        byte[] bytes = tabularExportService.toCsv(EXPORT_COLUMNS, response.getRows());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * XLSX rendering of the same statement export page (audit M5), using the identical column
     * layout as {@link #toCsv(StatementExportResponse)} so CSV and XLSX downloads of the same
     * request never drift apart.
     */
    public byte[] toXlsx(StatementExportResponse response) {
        return tabularExportService.toXlsx("Statement", EXPORT_COLUMNS, response.getRows());
    }

    private RowMapper<CursorRow> rowMapper() {
        return (rs, rowNum) -> {
            StatementRow row = new StatementRow();
            row.setId(rs.getLong("id"));
            Timestamp createdOn = rs.getTimestamp("created_on");
            row.setCreatedOn(value(rs.getString("created_on")));
            row.setGatewayId(value(rs.getString("gateway_id")));
            row.setTransactionType(value(rs.getString("tx_type")));
            row.setDescription(value(rs.getString("description")));
            row.setNarrative(value(rs.getString("narrative")));
            row.setAmount(decimal(rs.getBigDecimal("amount")));
            row.setCurrency(value(rs.getString("currency")));
            row.setMtnBalance(decimal(rs.getBigDecimal("mtnmm_balance")));
            row.setAirtelBalance(decimal(rs.getBigDecimal("airtelmm_balance")));
            row.setSafaricomBalance(decimal(rs.getBigDecimal("safaricom_balance")));
            row.setSmsBalance(decimal(rs.getBigDecimal("sms_balance")));
            row.setMerchantReference(value(rs.getString("tx_merchant_ref")));
            row.setTransactionId(value(rs.getString("tx_unique_id")));
            row.setTransactionStatus(value(rs.getString("status")));
            return new CursorRow(row, createdOn);
        };
    }

    private void validateMerchant(Merchant merchant, String merchantNumber) {
        if (merchant == null || !merchantNumber.equals(merchant.getAccount_number())) {
            throw new PaymentGatewayException("Verified merchant does not match request merchant");
        }
        validateActiveMerchant(merchant);
        if (!hasApi(merchant, Common.API_STATEMENT_EXPORT)) {
            throw new PaymentGatewayException(
                    "Merchant is not allowed to access " + Common.API_STATEMENT_EXPORT);
        }
    }

    private void validateActiveMerchant(Merchant merchant) {
        if (merchant == null) {
            throw new PaymentGatewayException("Merchant is required");
        }
        if (!"ACTIVE".equalsIgnoreCase(merchant.getStatus())) {
            throw new PaymentGatewayException("Merchant is not active");
        }
    }

    private boolean hasApi(Merchant merchant, String requiredApi) {
        String[] allowedApis = merchant.getAllowed_apis();
        if (allowedApis == null) {
            return false;
        }
        for (String api : allowedApis) {
            if (requiredApi.equals(api)) {
                return true;
            }
        }
        return false;
    }

    private LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new PaymentGatewayException(field + " must use YYYY-MM-DD");
        }
    }

    private int boundLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            throw new PaymentGatewayException("limit must be greater than 0");
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
