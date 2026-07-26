package net.citotech.cito.crossborder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.PaymentPartyRequest;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.api.v2.dto.TransferIntentRequest;
import net.citotech.cito.api.v2.dto.TransferIntentResponse;
import net.citotech.cito.compliance.RiskDecision;
import net.citotech.cito.compliance.RiskDecisionService;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.money.MoneyAmount;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrossBorderTransferService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FxQuoteService fxQuoteService;
    private final RiskDecisionService riskDecisionService;

    public CrossBorderTransferService(NamedParameterJdbcTemplate jdbcTemplate,
                                      FxQuoteService fxQuoteService,
                                      RiskDecisionService riskDecisionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fxQuoteService = fxQuoteService;
        this.riskDecisionService = riskDecisionService;
    }

    @Transactional
    public TransferIntentResponse createIntent(TransferIntentRequest request, Merchant merchant) {
        requireValue(request.getMerchantNumber(), "merchantNumber");
        String sourceCountry = country(request.getSourceCountry(), "sourceCountry");
        String targetCountry = country(request.getTargetCountry(), "targetCountry");
        String sourceCurrency = currency(request.getSourceCurrency(), "sourceCurrency");
        String targetCurrency = currency(request.getTargetCurrency(), "targetCurrency");
        BigDecimal sourceAmount = MoneyAmount.of(requireValue(request.getSourceAmount(), "sourceAmount")).asBigDecimal();
        requireValue(request.getBeneficiaryAccount(), "beneficiaryAccount");

        Corridor corridor = activeCorridor(sourceCountry, targetCountry, sourceCurrency, targetCurrency);
        enforceCorridorLimits(corridor, merchant.getId(), sourceAmount, sourceCurrency);

        FxQuoteRecord quote = null;
        BigDecimal targetAmount = sourceAmount;
        if (!sourceCurrency.equals(targetCurrency)) {
            quote = fxQuoteService.findActiveQuote(request.getQuoteReference(), merchant.getId());
            if (!quote.sourceCurrency().equals(sourceCurrency) || !quote.targetCurrency().equals(targetCurrency)) {
                throw new PaymentGatewayException("FX quote currencies do not match transfer intent");
            }
            if (quote.sourceAmount().compareTo(sourceAmount) != 0) {
                throw new PaymentGatewayException("FX quote amount does not match transfer intent");
            }
            targetAmount = quote.targetAmount();
        }

        String reference = "TRF-" + UUID.randomUUID();
        RiskDecision riskDecision = riskDecisionService.authorizePayment(
            merchant,
            riskRequest(request, reference, sourceCurrency, sourceAmount),
            "PAYOUT");
        reserveTreasury(sourceCurrency, sourceAmount);

        String status = "REVIEW".equalsIgnoreCase(riskDecision.getDecision()) ? "REVIEW_REQUIRED" : "READY";
        insertTransferIntent(reference, request, merchant.getId(), sourceAmount, targetAmount, status, riskDecision.getDecision(), quote);
        if (quote != null) {
            markQuoteBound(quote.quoteReference());
        }

        TransferIntentResponse response = new TransferIntentResponse();
        response.setIntentReference(reference);
        response.setQuoteReference(quote == null ? null : quote.quoteReference());
        response.setStatus(status);
        response.setRiskDecision(riskDecision.getDecision());
        response.setSourceAmount(sourceAmount);
        response.setSourceCurrency(sourceCurrency);
        response.setTargetAmount(targetAmount);
        response.setTargetCurrency(targetCurrency);
        response.setMessage("Transfer intent created and reserved for orchestration");
        return response;
    }

    private PaymentRequest riskRequest(TransferIntentRequest request, String reference, String currency, BigDecimal amount) {
        PaymentPartyRequest payee = new PaymentPartyRequest();
        payee.setType("ACCOUNT");
        payee.setValue(request.getBeneficiaryAccount());

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setMerchantNumber(request.getMerchantNumber());
        paymentRequest.setAmount(amount.toPlainString());
        paymentRequest.setCurrency(currency);
        paymentRequest.setCountry(request.getSourceCountry());
        paymentRequest.setReference(reference);
        paymentRequest.setDescription(blank(request.getDescription()) ? "Cross-border transfer intent" : request.getDescription());
        paymentRequest.setCallbackUrl("internal://cross-border-transfer-intent");
        paymentRequest.setPayee(payee);
        return paymentRequest;
    }

    private Corridor activeCorridor(String sourceCountry, String targetCountry, String sourceCurrency, String targetCurrency) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("source_country", sourceCountry);
        p.addValue("target_country", targetCountry);
        p.addValue("source_currency", sourceCurrency);
        p.addValue("target_currency", targetCurrency);
        List<Corridor> rows = jdbcTemplate.query(
            "SELECT id, single_transfer_limit, daily_limit FROM cross_border_corridors "
                + "WHERE source_country=:source_country AND target_country=:target_country "
                + "AND source_currency=:source_currency AND target_currency=:target_currency "
                + "AND corridor_status='ACTIVE' ORDER BY id ASC LIMIT 1",
            p,
            (rs, rowNum) -> new Corridor(
                rs.getLong("id"),
                rs.getBigDecimal("single_transfer_limit"),
                rs.getBigDecimal("daily_limit")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("No active cross-border corridor is configured");
        }
        return rows.get(0);
    }

    private void enforceCorridorLimits(Corridor corridor, long merchantId, BigDecimal amount, String currency) {
        if (corridor.singleTransferLimit() != null && amount.compareTo(corridor.singleTransferLimit()) > 0) {
            throw new PaymentGatewayException("Transfer exceeds corridor single-transfer limit");
        }
        if (corridor.dailyLimit() == null) {
            return;
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("currency", currency);
        p.addValue("today", LocalDate.now().toString());
        BigDecimal existing = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(source_amount), 0) FROM transfer_intents "
                + "WHERE merchant_id=:merchant_id AND source_currency=:currency "
                + "AND DATE(created_at)=:today AND intent_status NOT IN ('CANCELLED','FAILED')",
            p,
            BigDecimal.class);
        if ((existing == null ? BigDecimal.ZERO : existing).add(amount).compareTo(corridor.dailyLimit()) > 0) {
            throw new PaymentGatewayException("Transfer would exceed corridor daily limit");
        }
    }

    private void reserveTreasury(String currency, BigDecimal amount) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("currency", currency);
        List<TreasuryPosition> rows = jdbcTemplate.query(
            "SELECT available_balance, reserved_balance FROM treasury_positions "
                + "WHERE currency=:currency AND position_status='ACTIVE' LIMIT 1",
            p,
            (rs, rowNum) -> new TreasuryPosition(rs.getBigDecimal("available_balance"), rs.getBigDecimal("reserved_balance")));
        if (rows.isEmpty()) {
            return;
        }
        TreasuryPosition position = rows.get(0);
        if (position.availableBalance().subtract(position.reservedBalance()).compareTo(amount) < 0) {
            throw new PaymentGatewayException("Treasury position is insufficient for transfer intent");
        }
        p.addValue("amount", amount);
        jdbcTemplate.update(
            "UPDATE treasury_positions SET reserved_balance=reserved_balance+:amount "
                + "WHERE currency=:currency AND position_status='ACTIVE'",
            p);
    }

    private void insertTransferIntent(String reference,
                                      TransferIntentRequest request,
                                      long merchantId,
                                      BigDecimal sourceAmount,
                                      BigDecimal targetAmount,
                                      String status,
                                      String riskDecision,
                                      FxQuoteRecord quote) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("reference", reference);
        p.addValue("merchant_id", merchantId);
        p.addValue("quote_reference", quote == null ? null : quote.quoteReference());
        p.addValue("source_country", country(request.getSourceCountry(), "sourceCountry"));
        p.addValue("target_country", country(request.getTargetCountry(), "targetCountry"));
        p.addValue("source_currency", currency(request.getSourceCurrency(), "sourceCurrency"));
        p.addValue("target_currency", currency(request.getTargetCurrency(), "targetCurrency"));
        p.addValue("source_amount", sourceAmount);
        p.addValue("target_amount", targetAmount);
        p.addValue("beneficiary_account", request.getBeneficiaryAccount().trim());
        p.addValue("beneficiary_name", blank(request.getBeneficiaryName()) ? null : request.getBeneficiaryName().trim());
        p.addValue("status", status);
        p.addValue("risk_decision", riskDecision);
        jdbcTemplate.update(
            "INSERT INTO transfer_intents "
                + "(intent_reference, merchant_id, quote_reference, source_country, target_country, "
                + "source_currency, target_currency, source_amount, target_amount, beneficiary_account, "
                + "beneficiary_name, intent_status, risk_decision) "
                + "VALUES (:reference, :merchant_id, :quote_reference, :source_country, :target_country, "
                + ":source_currency, :target_currency, :source_amount, :target_amount, :beneficiary_account, "
                + ":beneficiary_name, :status, :risk_decision)",
            p);
    }

    private void markQuoteBound(String quoteReference) {
        jdbcTemplate.update(
            "UPDATE fx_quotes SET quote_status='BOUND' WHERE quote_reference=:reference",
            new MapSqlParameterSource("reference", quoteReference));
    }

    private String country(String value, String field) {
        String country = requireValue(value, field).toUpperCase(Locale.ROOT);
        if (!country.matches("[A-Z]{2}")) {
            throw new PaymentGatewayException(field + " must be an ISO country code");
        }
        return country;
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

    private record Corridor(long id, BigDecimal singleTransferLimit, BigDecimal dailyLimit) {
    }

    private record TreasuryPosition(BigDecimal availableBalance, BigDecimal reservedBalance) {
    }
}
