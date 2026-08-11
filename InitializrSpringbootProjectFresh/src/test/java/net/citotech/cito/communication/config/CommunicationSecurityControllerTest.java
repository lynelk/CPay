package net.citotech.cito.communication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.citotech.cito.communication.config.CommunicationSecurityController.CredentialRequest;
import net.citotech.cito.communication.config.CommunicationSecurityController.PolicyRequest;
import net.citotech.cito.communication.config.ProviderPolicyService.PolicyRow;
import net.citotech.cito.communication.credentials.CommunicationCredentialStore;
import net.citotech.cito.communication.credentials.CommunicationCredentialStore.CredentialRow;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link CommunicationSecurityController} (V54, track B6): the credential surface only ever
 * returns masked values, and the policy surface delegates to {@link ProviderPolicyService}.
 */
class CommunicationSecurityControllerTest {

    @Test
    void credentialsReturnsMaskedRowsForTheProvider() {
        CommunicationCredentialStore store = mock(CommunicationCredentialStore.class);
        when(store.listForProvider("YO_SMS"))
                .thenReturn(List.of(new CredentialRow("YO_SMS", "api_key", "se****et")));

        Map<String, Object> body =
                new CommunicationSecurityController(store, mock(ProviderPolicyService.class))
                        .credentials("yo_sms");

        assertThat(body.get("code")).isEqualTo("000");
        assertThat(body.get("providerCode")).isEqualTo("YO_SMS");
        assertThat((List<?>) body.get("credentials")).hasSize(1);
        verify(store).listForProvider("YO_SMS");
    }

    @Test
    void saveCredentialDelegatesAndReturnsMaskedRow() {
        CommunicationCredentialStore store = mock(CommunicationCredentialStore.class);
        when(store.save("YO_SMS", "api_key", "secret-value"))
                .thenReturn(new CredentialRow("YO_SMS", "api_key", "se****ue"));

        Map<String, Object> body =
                new CommunicationSecurityController(store, mock(ProviderPolicyService.class))
                        .saveCredential(new CredentialRequest("YO_SMS", "api_key", "secret-value"));

        assertThat(body.get("code")).isEqualTo("000");
        assertThat(((CredentialRow) body.get("credential")).maskedValue()).isEqualTo("se****ue");
        verify(store).save("YO_SMS", "api_key", "secret-value");
    }

    @Test
    void deleteCredentialDelegatesById() {
        CommunicationCredentialStore store = mock(CommunicationCredentialStore.class);
        when(store.delete("YO_SMS", "api_key")).thenReturn(1);

        Map<String, Object> body =
                new CommunicationSecurityController(store, mock(ProviderPolicyService.class))
                        .deleteCredential("yo_sms", "api_key");

        assertThat(body.get("code")).isEqualTo("000");
        assertThat(body.get("deleted")).isEqualTo(1);
        verify(store).delete("YO_SMS", "api_key");
    }

    @Test
    void policiesListsRows() {
        ProviderPolicyService policyService = mock(ProviderPolicyService.class);
        when(policyService.list())
                .thenReturn(
                        List.of(
                                new PolicyRow(
                                        "LEGACY_SETTINGS",
                                        60,
                                        1000,
                                        10000,
                                        30000,
                                        true,
                                        true,
                                        null,
                                        null)));

        Map<String, Object> body =
                new CommunicationSecurityController(
                                mock(CommunicationCredentialStore.class), policyService)
                        .policies();

        assertThat(body.get("code")).isEqualTo("000");
        assertThat((List<?>) body.get("policies")).hasSize(1);
    }

    @Test
    void savePolicyDelegatesAndReturnsThePersistedRow() {
        ProviderPolicyService policyService = mock(ProviderPolicyService.class);
        PolicyRow saved = new PolicyRow("YO_SMS", 80, 2000, 5000, 15000, true, true, null, null);
        when(policyService.save("YO_SMS", 80, 2000, 5000, 15000, true)).thenReturn(saved);

        Map<String, Object> body =
                new CommunicationSecurityController(
                                mock(CommunicationCredentialStore.class), policyService)
                        .savePolicy(new PolicyRequest("YO_SMS", 80, 2000, 5000, 15000, true));

        assertThat(body.get("code")).isEqualTo("000");
        assertThat(((PolicyRow) body.get("policy")).maxPerMinute()).isEqualTo(80);
        verify(policyService).save("YO_SMS", 80, 2000, 5000, 15000, true);
    }
}
