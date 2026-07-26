package net.citotech.cito.compliance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.security.CanonicalRequestSigner;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SanctionsScreeningService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SanctionsScreeningService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public RiskDecision screenPayment(Merchant merchant, PaymentRequest request, String direction) {
        List<ScreeningCandidate> candidates = candidates(merchant, request, direction);
        for (ScreeningCandidate candidate : candidates) {
            WatchlistHit hit = findHit(candidate);
            if (hit != null) {
                recordHit(merchant.getId(), request.getReference(), direction, candidate, hit);
                if ("BLOCK".equalsIgnoreCase(hit.action())) {
                    return RiskDecision.block("SANCTIONS_MATCH", "Watchlist match: " + hit.summary());
                }
                return RiskDecision.review("SANCTIONS_REVIEW", "Watchlist review required: " + hit.summary());
            }
        }
        return RiskDecision.allow("Sanctions screening passed");
    }

    private List<ScreeningCandidate> candidates(Merchant merchant, PaymentRequest request, String direction) {
        List<ScreeningCandidate> candidates = new ArrayList<>();
        if (!blank(merchant.getName())) {
            candidates.add(new ScreeningCandidate("MERCHANT_NAME", merchant.getName()));
        }
        if (!blank(merchant.getAccount_number())) {
            candidates.add(new ScreeningCandidate("MERCHANT_ACCOUNT", merchant.getAccount_number()));
        }
        if ("PAYOUT".equalsIgnoreCase(direction) && request.getPayee() != null) {
            candidates.add(new ScreeningCandidate(type(request.getPayee().getType(), "PAYEE"), request.getPayee().getValue()));
        }
        if (!"PAYOUT".equalsIgnoreCase(direction) && request.getPayer() != null) {
            candidates.add(new ScreeningCandidate(type(request.getPayer().getType(), "PAYER"), request.getPayer().getValue()));
        }
        return candidates.stream().filter(candidate -> !blank(candidate.value())).toList();
    }

    private WatchlistHit findHit(ScreeningCandidate candidate) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("types", List.of(candidate.type(), "ANY"));
        p.addValue("hash", hash(candidate.value()));
        List<WatchlistHit> hits = jdbcTemplate.query(
            "SELECT id, list_name, risk_rating, action, COALESCE(entry_label, list_name) AS summary "
                + "FROM compliance_watchlist_entries "
                + "WHERE active_flag='YES' AND entry_type IN (:types) AND entry_value_hash=:hash "
                + "ORDER BY CASE WHEN action='BLOCK' THEN 0 ELSE 1 END, id ASC LIMIT 1",
            p,
            (rs, rowNum) -> new WatchlistHit(
                rs.getLong("id"),
                rs.getString("list_name"),
                rs.getString("risk_rating"),
                rs.getString("action"),
                rs.getString("summary")));
        return hits.isEmpty() ? null : hits.get(0);
    }

    private void recordHit(long merchantId,
                           String reference,
                           String direction,
                           ScreeningCandidate candidate,
                           WatchlistHit hit) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("reference", reference);
        p.addValue("direction", direction);
        p.addValue("screened_type", candidate.type());
        p.addValue("value_hash", hash(candidate.value()));
        p.addValue("watchlist_id", hit.id());
        p.addValue("decision", "BLOCK".equalsIgnoreCase(hit.action()) ? "BLOCK" : "REVIEW");
        p.addValue("summary", hit.summary());
        jdbcTemplate.update(
            "INSERT INTO compliance_screening_hits "
                + "(merchant_id, request_reference, direction, screened_type, screened_value_hash, "
                + "watchlist_entry_id, decision, hit_summary) "
                + "VALUES (:merchant_id, :reference, :direction, :screened_type, :value_hash, "
                + ":watchlist_id, :decision, :summary)",
            p);
    }

    private String type(String provided, String fallback) {
        return blank(provided) ? fallback : provided.trim().toUpperCase(Locale.ROOT);
    }

    private String hash(String value) {
        return CanonicalRequestSigner.sha256Hex(normalize(value));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record ScreeningCandidate(String type, String value) {
    }

    private record WatchlistHit(long id, String listName, String riskRating, String action, String summary) {
    }
}
