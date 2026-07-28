package net.citotech.cito.gateway;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProviderTokenControllerTest {

    @Test
    void savesProviderTokenFromRequestBody() {
        ProviderTokenStoreService tokenStoreService = mock(ProviderTokenStoreService.class);
        ProviderTokenController controller = new ProviderTokenController(tokenStoreService);

        controller.save(new ProviderTokenController.ProviderTokenSaveRequest(
            "mtn",
            "collections",
            "",
            "secret-token",
            "2026-12-31T00:00:00Z"));

        verify(tokenStoreService).save(
            eq("mtn"),
            eq("collections"),
            eq("PRODUCTION"),
            eq("secret-token"),
            eq(Instant.parse("2026-12-31T00:00:00Z")));
    }
}
