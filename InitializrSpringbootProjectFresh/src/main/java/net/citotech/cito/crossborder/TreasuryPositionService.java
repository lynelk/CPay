package net.citotech.cito.crossborder;

import java.math.BigDecimal;
import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-side view over {@code treasury_positions} (V12). The write side (reserve/release/capture in
 * {@link CrossBorderTransferService} and the payout paths) already maintains available vs reserved
 * balances; this service is the missing ops surface - finance/operations could not previously see
 * the treasury position without querying the database directly.
 */
@Service
public class TreasuryPositionService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TreasuryPositionService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** All active treasury positions, with the net available (available - reserved) computed. */
    public List<TreasuryPositionRow> listPositions() {
        return jdbcTemplate.query(
                "SELECT currency, available_balance, reserved_balance, position_status, updated_at "
                        + "FROM treasury_positions WHERE position_status='ACTIVE' "
                        + "ORDER BY currency",
                new MapSqlParameterSource(),
                (rs, rowNum) ->
                        new TreasuryPositionRow(
                                rs.getString("currency"),
                                rs.getBigDecimal("available_balance"),
                                rs.getBigDecimal("reserved_balance"),
                                rs.getString("position_status"),
                                rs.getString("updated_at")));
    }

    /** One position by currency, null when absent or inactive. */
    public TreasuryPositionRow findByCurrency(String currency) {
        String code = currency == null ? "" : currency.trim().toUpperCase();
        if (!code.matches("[A-Z]{3}")) {
            throw new PaymentGatewayException("currency must be an ISO currency code");
        }
        MapSqlParameterSource p = new MapSqlParameterSource("currency", code);
        List<TreasuryPositionRow> rows =
                jdbcTemplate.query(
                        "SELECT currency, available_balance, reserved_balance, position_status, updated_at "
                                + "FROM treasury_positions WHERE currency=:currency AND position_status='ACTIVE' "
                                + "ORDER BY id DESC LIMIT 1",
                        p,
                        (rs, rowNum) ->
                                new TreasuryPositionRow(
                                        rs.getString("currency"),
                                        rs.getBigDecimal("available_balance"),
                                        rs.getBigDecimal("reserved_balance"),
                                        rs.getString("position_status"),
                                        rs.getString("updated_at")));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public record TreasuryPositionRow(
            String currency,
            BigDecimal availableBalance,
            BigDecimal reservedBalance,
            String status,
            String updatedAt) {

        public BigDecimal netAvailable() {
            BigDecimal available = availableBalance == null ? BigDecimal.ZERO : availableBalance;
            BigDecimal reserved = reservedBalance == null ? BigDecimal.ZERO : reservedBalance;
            return available.subtract(reserved);
        }
    }
}
