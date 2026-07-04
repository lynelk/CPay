package net.citotech.cito.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvStatementParserTest {
    @Test
    void parsesStandardProviderCsv() {
        CsvStatementParser parser = new CsvStatementParser("MTN", "mtn_momo");
        String csv = "provider_reference,merchant_reference,amount,currency,transaction_date\nNET123,MER123,1000.55,UGX,2026-07-03";
        List<StatementRow> rows = parser.parse(csv);
        assertEquals(1, rows.size());
        assertEquals("NET123", rows.get(0).providerReference);
        assertEquals("MER123", rows.get(0).merchantReference);
        assertEquals("1000.55", rows.get(0).amount.toPlainString());
    }
}
