package net.citotech.cito.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One daily float/stock account balance reading (audit O3), as stored in
 * {@code float_balance_snapshots}.
 */
public record BalanceSnapshotPoint(LocalDate date, BigDecimal balance) {
}
