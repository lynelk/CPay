package net.citotech.cito.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdentityProviderCapabilityContractTest {

    @Test
    void synchronousSupportRequiresBothCoverageAndSyncCapability() {
        IdentityVerificationConnector asyncOnly = new IdentityVerificationConnector() {
            @Override public String providerCode() { return "async-only"; }
            @Override public boolean supportsSync() { return false; }
            @Override public boolean supportsAsync() { return true; }
            @Override public Set<String> supportedIdentityTypes() { return Set.of("PASSPORT"); }
            @Override public Set<String> supportedCountries() { return Set.of("UG"); }
            @Override public IdentityRecords.VerifiedIdentity verify(IdentityRecords.IdentityVerificationRequest request) { throw new UnsupportedOperationException(); }
            @Override public IdentityRecords.VerifiedIdentity parseCallback(String body, Map<String, String> headers) { throw new UnsupportedOperationException(); }
            @Override public boolean validateCallbackHeaders(Map<String, String> headers) { return true; }
        };

        assertFalse(asyncOnly.supports("PASSPORT", "UG"));
        assertTrue(asyncOnly.supportedIdentityTypes().contains("PASSPORT"));
        assertTrue(asyncOnly.supportedCountries().contains("UG"));
    }

    @Test
    void defaultConnectorCoverageRemainsConservative() {
        IdentityVerificationConnector connector = new IdentityVerificationConnector() {
            @Override public String providerCode() { return "default"; }
            @Override public boolean supportsSync() { return true; }
            @Override public boolean supportsAsync() { return false; }
            @Override public IdentityRecords.VerifiedIdentity verify(IdentityRecords.IdentityVerificationRequest request) { throw new UnsupportedOperationException(); }
            @Override public IdentityRecords.VerifiedIdentity parseCallback(String body, Map<String, String> headers) { throw new UnsupportedOperationException(); }
            @Override public boolean validateCallbackHeaders(Map<String, String> headers) { return true; }
        };

        assertTrue(connector.supports("NIN", "UG"));
        assertFalse(connector.supports("PASSPORT", "UG"));
    }
}
