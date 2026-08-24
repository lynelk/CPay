package net.citotech.cito.financialmessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FinancialMessagingStandardsTest {

    @Test
    void validatesAndNormalizesBicStructureWithoutClaimingRegistryValidation() {
        assertTrue(BicValidator.isStructurallyValid("BARCGB22"));
        assertTrue(BicValidator.isStructurallyValid("DEUTDEFF500"));
        assertEquals("BARCGB22", BicValidator.normalize("barcgb22"));
        assertEquals("GB", BicValidator.countryCode("BARCGB22"));
        assertNull(BicValidator.branchCode("BARCGB22"));
        assertEquals("500", BicValidator.branchCode("DEUTDEFF500"));

        assertFalse(BicValidator.isStructurallyValid("TOO-SHORT"));
        assertFalse(BicValidator.isStructurallyValid("1234GB22"));
        assertThrows(IllegalArgumentException.class, () -> BicValidator.normalize("NOT-A-BIC"));
    }

    @Test
    void sanitizesSensitiveIso8583FieldsAndDoesNotMutateInput() {
        Map<Integer, String> fields = new LinkedHashMap<>();
        fields.put(2, "4111111111111111");
        fields.put(3, "000000");
        fields.put(14, "2912");
        fields.put(35, "4111111111111111=29122010000012345678");
        fields.put(52, "0123456789ABCDEF");
        fields.put(55, "9F2608DEADBEEFCAFEBABE");

        var sanitized = Iso8583MessageSanitizer.sanitize("0200", fields);

        assertEquals("0200", sanitized.mti());
        assertEquals("************1111", sanitized.fields().get(2));
        assertEquals("000000", sanitized.fields().get(3));
        assertEquals("[REDACTED-EXPIRY]", sanitized.fields().get(14));
        assertEquals("[REDACTED-TRACK-DATA]", sanitized.fields().get(35));
        assertEquals("[REDACTED-PIN-BLOCK]", sanitized.fields().get(52));
        assertEquals("[REDACTED-EMV-DATA]", sanitized.fields().get(55));
        assertEquals("4111111111111111", fields.get(2));
        assertThrows(UnsupportedOperationException.class, () -> sanitized.fields().put(4, "100"));
    }

    @Test
    void rejectsInvalidMtiAndFieldNumbers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Iso8583MessageSanitizer.sanitize("200", Map.of(3, "000000")));
        assertThrows(
                IllegalArgumentException.class,
                () -> Iso8583MessageSanitizer.sanitize("0200", Map.of(1, "bitmap")));
        assertThrows(
                IllegalArgumentException.class,
                () -> Iso8583MessageSanitizer.sanitize("0200", Map.of(129, "nope")));
    }

    @Test
    void iso20022ParserAcceptsNormalXmlAndRejectsDoctypeEntityPayloads() {
        byte[] normal =
                "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:test\"><MessageId>ABC</MessageId></Document>"
                        .getBytes(StandardCharsets.UTF_8);
        assertEquals("Document", Iso20022XmlValidator.parseSecure(normal).getDocumentElement().getLocalName());

        byte[] xxe =
                ("<?xml version=\"1.0\"?>"
                                + "<!DOCTYPE x [<!ENTITY ext SYSTEM \"file:///etc/passwd\">]>"
                                + "<Document>&ext;</Document>")
                        .getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> Iso20022XmlValidator.parseSecure(xxe));
    }
}
