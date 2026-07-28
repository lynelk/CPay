package net.citotech.cito.api.v2;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.StatementExportResponse;
import net.citotech.cito.api.v2.dto.StatementExportResponse.StatementRow;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MerchantStatementExportService {
    private static final int DEFAULT_LIMIT = 1000;
    private static final int MAX_LIMIT = 5000;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MerchantReadAuditService auditService;

    public MerchantStatementExportService(NamedParameterJdbcTemplate jdbcTemplate,
                                          MerchantReadAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    public StatementExportResponse export(Merchant merchant, String merchantNumber, String startDate, String endDate, Integer limit) {
        validateMerchant(merchant, merchantNumber);
        LocalDate start = parseDate(startDate, "startDate");
        LocalDate end = parseDate(endDate, "endDate");
        if (end.isBefore(start)) {
            throw new PaymentGatewayException("endDate must be on or after startDate");
        }
        int boundedLimit = boundLimit(limit);

        String sql = "SELECT ms.id, ms.created_on, ms.gateway_id, ms.tx_type, ms.description, ms.narrative, "
                + "ms.amount, ms.currency, ms.mtnmm_balance, ms.airtelmm_balance, ms.safaricom_balance, ms.sms_balance, "
                + "tx.tx_merchant_ref, tx.tx_unique_id, tx.status "
                + "FROM " + Common.DB_TABLE_MERCHANT_STATEMENT + " ms "
                + "LEFT JOIN " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + " tx ON tx.id = ms.transactions_log_id "
                + "WHERE ms.merchant_id = :merchant_id "
                + "AND ms.created_on >= :start_at "
                + "AND ms.created_on < :end_at "
                + "ORDER BY ms.created_on DESC, ms.id DESC "
                + "LIMIT :limit";

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchant.getId());
        p.addValue("start_at", Timestamp.valueOf(start.atStartOfDay()));
        p.addValue("end_at", Timestamp.valueOf(end.plusDays(1).atStartOfDay()));
        p.addValue("limit", boundedLimit);

        List<StatementRow> rows = jdbcTemplate.query(sql, p, rowMapper());
        auditService.record(merchant, "STATEMENT_EXPORT_READ", start + " to " + end + ", rows=" + rows.size());

        StatementExportResponse response = new StatementExportResponse();
        response.setMerchantNumber(merchantNumber);
        response.setStartDate(start.toString());
        response.setEndDate(end.toString());
        response.setCount(rows.size());
        response.setRows(rows);
        return response;
    }

    public String toCsv(StatementExportResponse response) {
        StringBuilder csv = new StringBuilder();
        csv.append("id,created_on,gateway_id,transaction_type,amount,currency,merchant_reference,transaction_id,status,description,narrative,mtn_balance,airtel_balance,safaricom_balance,sms_balance\n");
        for (StatementRow row : response.getRows()) {
            csv.append(row.getId()).append(',')
                .append(csv(row.getCreatedOn())).append(',')
                .append(csv(row.getGatewayId())).append(',')
                .append(csv(row.getTransactionType())).append(',')
                .append(csv(row.getAmount())).append(',')
                .append(csv(row.getCurrency())).append(',')
                .append(csv(row.getMerchantReference())).append(',')
                .append(csv(row.getTransactionId())).append(',')
                .append(csv(row.getTransactionStatus())).append(',')
                .append(csv(row.getDescription())).append(',')
                .append(csv(row.getNarrative())).append(',')
                .append(csv(row.getMtnBalance())).append(',')
                .append(csv(row.getAirtelBalance())).append(',')
                .append(csv(row.getSafaricomBalance())).append(',')
                .append(csv(row.getSmsBalance())).append('\n');
        }
        return csv.toString();
    }

    private RowMapper<StatementRow> rowMapper() {
        return (rs, rowNum) -> {
            StatementRow row = new StatementRow();
            row.setId(rs.getLong("id"));
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
            return row;
        };
    }

    private void validateMerchant(Merchant merchant, String merchantNumber) {
        if (merchant == null || !merchantNumber.equals(merchant.getAccount_number())) {
            throw new PaymentGatewayException("Verified merchant does not match request merchant");
        }
        if (!"ACTIVE".equalsIgnoreCase(merchant.getStatus())) {
            throw new PaymentGatewayException("Merchant is not active");
        }
        if (!hasApi(merchant, Common.API_STATEMENT_EXPORT)) {
            throw new PaymentGatewayException("Merchant is not allowed to access " + Common.API_STATEMENT_EXPORT);
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

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
