package net.citotech.cito.crossborder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.FxQuoteRequest;
import net.citotech.cito.api.v2.dto.FxQuoteResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.money.MoneyAmount;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FxQuoteService {
    private static final int MONEY_SCALE = 4;
    private static final int RATE_SCALE = 10;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FxQuoteService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public FxQuoteResponse createQuote(FxQuoteRequest request, Merchant merchant) {
        requireValue(request.getMerchantNumber(), "merchantNumber");
        String sourceCurrency = currency(request.getSourceCurrency(), "sourceCurrency");
        String targetCurrency = currency(request.getTargetCurrency(), "targetCurrency");
        BigDecimal sourceAmount = MoneyAmount.of(requireValue(request.getSourceAmount(), "sourceAmount")).asBigDecimal();
        BigDecimal rate = sourceCurrency.equals(targetCurrency) ? BigDecimal.ONE.setScale(RATE_SCALE, RoundingMode.HALF_UP) : latestRate(sourceCurrency, targetCurrency);
        BigDecimal targetAmount = sourceAmount.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        String reference = "FX-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("reference", reference);
        p.addValue("merchant_id", merchant.getId());
        p.addValue("source_currency", sourceCurrency);
        p.addValue("target_currency", targetCurrency);
        p.addValue("source_amount", sourceAmount);
        p.addValue("target_amount", targetAmount);
        p.addValue("rate", rate);
        p.addValue("expires_at", Timestamp.from(expiresAt));
        jdbcTemplate.update(
            "INSERT INTO fx_quotes "
                + "(quote_reference, merchant_id, source_currency, target_currency, source_amount, target_amount, rate, expires_at) "
                + "VALUES (:reference, :merchant_id, :source_currency, :target_currency, :source_amount, :target_amount, :rate, :expires_at)",
            p);
        return new FxQuoteResponse(reference, sourceCurrency, targetCurrency, sourceAmount, targetAmount, rate, expiresAt);
    }

    public FxQuoteRecord findActiveQuote(String quoteReference, long merchantId) {
        if (blank(quoteReference)) {
            throw new PaymentGatewayException("quoteReference is required for cross-currency transfer intents");
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("reference", quoteReference.trim());
        p.addValue("merchant_id", merchantId);
        List<FxQuoteRecord> rows = jdbcTemplate.query(
            "SELECT quote_reference, merchant_id, source_currency, target_currency, source_amount, target_amount, rate, expires_at "
                + "FROM fx_quotes "
                + "WHERE quote_reference=:reference AND merchant_id=:merchant_id "
                + "AND quote_status='ACTIVE' AND expires_at > CURRENT_TIMESTAMP LIMIT 1",
            p,
            (rs, rowNum) -> new FxQuoteRecord(
                rs.getString("quote_reference"),
                rs.getLong("merchant_id"),
                rs.getString("source_currency"),
                rs.getString("target_currency"),
                rs.getBigDecimal("source_amount"),
                rs.getBigDecimal("target_amount"),
                rs.getBigDecimal("rate"),
                rs.getTimestamp("expires_at").toInstant()));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("FX quote is expired or was not found");
        }
        return rows.get(0);
    }

    private BigDecimal latestRate(String sourceCurrency, String targetCurrency) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("source_currency", sourceCurrency);
        p.addValue("target_currency", targetCurrency);
        List<BigDecimal> rates = jdbcTemplate.query(
            "SELECT rate FROM fx_rates "
                + "WHERE source_currency=:source_currency AND target_currency=:target_currency "
                + "AND rate_status='ACTIVE' AND (valid_until IS NULL OR valid_until > CURRENT_TIMESTAMP) "
                + "ORDER BY valid_from DESC LIMIT 1",
            p,
            (rs, rowNum) -> rs.getBigDecimal("rate"));
        if (rates.isEmpty()) {
            throw new PaymentGatewayException("No active FX rate for " + sourceCurrency + "/" + targetCurrency);
        }
        return rates.get(0).setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private String currency(String value, String field) {
        String currency = requireValue(value, field).toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new PaymentGatewayException(field + " must be an ISO currency code");
        }
        return currency;
    }

    private String requireValue(String value, String field) {
        if (blank(value)) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
