package net.citotech.cito.ledger;

import java.math.BigDecimal;

public record LedgerEntryCommand(
    String accountCode,
    String accountName,
    String accountType,
    String ownerType,
    Long ownerId,
    String direction,
    BigDecimal amount,
    String currency,
    String memo
) {
}
