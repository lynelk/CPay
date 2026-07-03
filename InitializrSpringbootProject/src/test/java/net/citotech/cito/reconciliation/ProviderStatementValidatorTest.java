package net.citotech.cito.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderStatementValidatorTest {
    @Test
    void detectsDuplicateProviderRows() {
        CsvStatementParser parser = new CsvStatementParser("MTN", "mtn_momo");
        String csv = "provider_reference,merchant_reference,amount,currency\nA1,M1,1000,UGX\nA1,M2,1000,UGX";
        List<StatementRow> rows = parser.parse(csv);
        assertEquals(2, rows.size());
        assertEquals(rows.get(0).providerReference, rows.get(1).providerReference);
    }
}
