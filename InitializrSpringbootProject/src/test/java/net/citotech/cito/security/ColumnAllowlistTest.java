package net.citotech.cito.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColumnAllowlistTest {

    @Test
    void validateAllowedColumnReturnsLowercase() {
        assertEquals("name", ColumnAllowlist.validate("admins", "name"));
        assertEquals("email", ColumnAllowlist.validate("admins", "email"));
        assertEquals("status", ColumnAllowlist.validate("merchants", "status"));
    }

    @Test
    void validateRejectionThrowsForUnknownColumn() {
        assertThrows(IllegalArgumentException.class,
                () -> ColumnAllowlist.validate("admins", "1=1; DROP TABLE admins;--"));
    }

    @Test
    void validateRejectionThrowsForUnionKeyword() {
        assertThrows(IllegalArgumentException.class,
                () -> ColumnAllowlist.validate("admins", "union"));
    }

    @Test
    void validateRejectionThrowsForEmptyColumn() {
        assertThrows(IllegalArgumentException.class,
                () -> ColumnAllowlist.validate("admins", ""));
    }

    @Test
    void validateRejectionThrowsForNullColumn() {
        assertThrows(IllegalArgumentException.class,
                () -> ColumnAllowlist.validate("admins", null));
    }

    @Test
    void validateDefaultFallbackAllowsCommonColumns() {
        assertEquals("name", ColumnAllowlist.validate("name"));
        assertEquals("status", ColumnAllowlist.validate("status"));
        assertEquals("email", ColumnAllowlist.validate("email"));
    }

    @Test
    void validateDefaultFallbackRejectsUnknownColumn() {
        assertThrows(IllegalArgumentException.class,
                () -> ColumnAllowlist.validate("arbitrary_unknown_col"));
    }

    @Test
    void validateMerchantTransactionsColumns() {
        assertEquals("gateway_id", ColumnAllowlist.validate("merchant_transactions_log", "gateway_id"));
        assertEquals("payer_number", ColumnAllowlist.validate("merchant_transactions_log", "payer_number"));
        assertThrows(IllegalArgumentException.class,
                () -> ColumnAllowlist.validate("merchant_transactions_log", "password"));
    }
}
