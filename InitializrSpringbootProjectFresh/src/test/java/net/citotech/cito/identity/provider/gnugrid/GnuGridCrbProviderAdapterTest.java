package net.citotech.cito.identity.provider.gnugrid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.gateway.ProviderToken;
import net.citotech.cito.gateway.ProviderTokenStoreService;
import net.citotech.cito.identity.domain.CheckOutcome;
import net.citotech.cito.identity.domain.ValidationCapability;
import net.citotech.cito.identity.provider.CheckContext;
import net.citotech.cito.identity.provider.ProviderCheckRequest;
import net.citotech.cito.identity.provider.ProviderCheckResult;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * gnuGrid CRB provider-adapter contract tests (ISO domain mapping: identity/provider/gnugrid).
 * Follows the repo's outbound-executor injection pattern ({@link Common#setOutboundHttpExecutor}):
 * provider HTTP behaviour is simulated without a real network. A valid access token is stubbed in
 * the token store so the injected executor only ever answers ID-validation calls. The adapter must
 * never convert a technical provider failure into an identity rejection ({@link CheckOutcome#ERROR}
 * vs {@code FAIL}).
 */
class GnuGridCrbProviderAdapterTest {

    private static final String TOKEN_ENV = "SANDBOX";

    @AfterEach
    void resetHttpExecutor() {
        Common.setOutboundHttpExecutor(null);
    }

    @Test
    void matchedNinMapsToPassWithNinMatchReason() {
        GnuGridCrbProviderAdapter adapter =
                adapterWith(
                        "{\"verified\":true,\"providerReference\":\"gnu-1\","
                                + "\"fullName\":\"JANE DOE\",\"expiresAt\":\"2027-01-01T00:00:00Z\"}",
                        200);

        ProviderCheckResult result = adapter.execute(ninRequest("nin-valid-1"));

        assertEquals(CheckOutcome.PASS, result.outcome());
        assertTrue(result.reasonCodes().contains("NIN_MATCH"));
        assertEquals("gnu-1", result.externalReference());
        assertEquals("JANE DOE", result.attributes().get("fullName"));
    }

    @Test
    void notFoundNinMapsToFailAsIdentityNotFound() {
        GnuGridCrbProviderAdapter adapter =
                adapterWith("{\"verified\":false,\"providerReference\":\"gnu-2\"}", 200);

        ProviderCheckResult result = adapter.execute(ninRequest("nin-not-found"));

        assertEquals(CheckOutcome.FAIL, result.outcome());
        assertTrue(result.reasonCodes().contains("IDENTITY_NOT_FOUND"));
        assertEquals("gnu-2", result.externalReference());
    }

    @Test
    void providerTransportFailureMapsToErrorNeverToFail() {
        GnuGridCrbProviderAdapter adapter = adapterWith("", 0);

        ProviderCheckResult result = adapter.execute(ninRequest("nin-timeout"));

        assertEquals(CheckOutcome.ERROR, result.outcome());
        assertTrue(result.reasonCodes().contains("PROVIDER_TEMPORARILY_UNAVAILABLE"));
    }

    @Test
    void providerAuthErrorMapsToTechnicalError() {
        GnuGridCrbProviderAdapter adapter = adapterWith("{\"error\":\"invalid token\"}", 401);

        ProviderCheckResult result = adapter.execute(ninRequest("nin-auth"));

        assertEquals(CheckOutcome.ERROR, result.outcome());
        assertTrue(result.reasonCodes().contains("PROVIDER_AUTHENTICATION_ERROR"));
    }

    @Test
    void malformedProviderBodyMapsToInconclusiveError() {
        GnuGridCrbProviderAdapter adapter = adapterWith("this-is-not-json", 200);

        ProviderCheckResult result = adapter.execute(ninRequest("nin-malformed"));

        assertEquals(CheckOutcome.ERROR, result.outcome());
        assertTrue(result.reasonCodes().contains("PROVIDER_INCONCLUSIVE"));
    }

    @Test
    void unsupportedCapabilityThrows() {
        GnuGridCrbProviderAdapter adapter = adapterWith("{\"verified\":true}", 200);

        ProviderCheckRequest request =
                new ProviderCheckRequest(7L, 99L, ValidationCapability.EMAIL, "UG", Map.of());

        assertThrows(UnsupportedOperationException.class, () -> adapter.execute(request));
    }

    @Test
    void adapterAdvertisesCrbCapabilitiesAndRejectsUnsupportedContext() {
        GnuGridCrbProviderAdapter adapter = adapterWith("{\"verified\":true}", 200);

        assertTrue(adapter.capabilities().contains(ValidationCapability.CREDIT_SCORE_CRB));
        assertTrue(adapter.capabilities().contains(ValidationCapability.CREDIT_REPORT));
        assertFalse(
                adapter.supports(new CheckContext(7L, ValidationCapability.EMAIL, "UG", null)));
        assertEquals("GNUGRID_CRB", adapter.providerCode());
    }

    private GnuGridCrbProviderAdapter adapterWith(String responseBody, int status) {
        Common.setOutboundHttpExecutor(
                (method, url, body, headers) -> {
                    HttpRequestResponse response = new HttpRequestResponse();
                    response.setStatusCode(status);
                    response.setResponse(responseBody);
                    return response;
                });
        GnuGridProperties properties =
                new GnuGridProperties(
                        "https://gnugrid.example",
                        "SANDBOX",
                        "/v1/oauth/token",
                        "/v1/verifications",
                        "/v1/verifications/phone",
                        "/v1/enquiries",
                        "/v1/credit-enquiries/credit-scores",
                        60L,
                        2,
                        10);
        GnuGridOauthClient oauthClient =
                new GnuGridOauthClient(properties, "client-id", "client-secret");
        GnuGridTokenManager tokenManager =
                new GnuGridTokenManager(mockTokenStore(), oauthClient, properties);
        GnuGridIdValidationClient idClient =
                new GnuGridIdValidationClient(properties, tokenManager);
        return new GnuGridCrbProviderAdapter(idClient);
    }

    private ProviderCheckRequest ninRequest(String nin) {
        return new ProviderCheckRequest(
                7L,
                1L,
                ValidationCapability.NIN,
                "UG",
                Map.of(
                        "nin", nin,
                        "fullName", "JANE DOE",
                        "msisdn", "+256700000000"));
    }

    private ProviderTokenStoreService mockTokenStore() {
        ProviderTokenStoreService store = Mockito.mock(ProviderTokenStoreService.class);
        ProviderToken token = new ProviderToken();
        token.setTokenValue("test-access-token");
        token.setExpiresAt(Instant.now().plusSeconds(300));
        Mockito.when(
                        store.findValid(
                                GnuGridTokenManager.PROVIDER_CODE,
                                GnuGridTokenManager.SEGMENT,
                                TOKEN_ENV))
                .thenReturn(Optional.of(token));
        Mockito.when(
                        store.acquireRefreshLease(
                                eq(GnuGridTokenManager.PROVIDER_CODE),
                                eq(GnuGridTokenManager.SEGMENT),
                                eq(TOKEN_ENV),
                                anyString(),
                                any()))
                .thenReturn(true);
        return store;
    }
}
