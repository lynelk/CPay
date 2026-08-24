package net.citotech.cito.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GenericIdentityVerificationContractTest {

    @Test
    void genericRequestNormalizesDocumentMetadata() {
        IdentityRecords.IdentityVerificationRequest request =
                new IdentityRecords.IdentityVerificationRequest(
                        "IDV-123",
                        9L,
                        " passport ",
                        " ug ",
                        " ab12345 ",
                        "Amina Example",
                        "+256772000000");

        assertEquals("PASSPORT", request.identityType());
        assertEquals("UG", request.country());
        assertEquals("AB12345", request.identityNumber());
    }

    @Test
    void legacyNinConstructorRemainsBackwardCompatible() {
        IdentityRecords.IdentityVerificationRequest request =
                new IdentityRecords.IdentityVerificationRequest(
                        "IDV-124", 10L, "cm123456789012", "Amina Example", "+256772000000");

        assertEquals("NIN", request.identityType());
        assertEquals("UG", request.country());
        assertEquals("CM123456789012", request.nin());
    }

    @Test
    void providerCoverageFailsClosedForUnsupportedDocumentTypes() {
        GnuGridConnector connector = new GnuGridConnector("", "");
        assertTrue(connector.supports("NIN", "UG"));
        assertFalse(connector.supports("PASSPORT", "UG"));

        IdentityRecords.IdentityVerificationRequest request =
                new IdentityRecords.IdentityVerificationRequest(
                        "IDV-125",
                        11L,
                        "PASSPORT",
                        "UG",
                        "AB12345",
                        "Amina Example",
                        "+256772000000");

        assertThrows(IdentityVerificationException.class, () -> connector.verify(request));
    }
}
