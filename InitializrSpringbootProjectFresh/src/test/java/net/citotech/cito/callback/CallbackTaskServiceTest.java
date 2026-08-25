package net.citotech.cito.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import net.citotech.cito.metrics.GatewayMetrics;
import org.junit.jupiter.api.Test;

class CallbackTaskServiceTest {

    @Test
    void callbackHeadersExposeEveryFieldRequiredToVerifyCallbackV1Signature() {
        CallbackTaskService service =
                new CallbackTaskService(
                        mock(CallbackTaskRepository.class),
                        mock(CallbackClaimRepository.class),
                        mock(CallbackSigningService.class),
                        mock(GatewayMetrics.class));

        CallbackTask task = new CallbackTask();
        task.id = 42L;
        task.merchantId = 10003482L;
        task.referenceValue = "opfin-ref-123";

        CallbackSigningService.SignedCallback signed =
                new CallbackSigningService.SignedCallback(
                        "base64-signature", "nonce-value", "1787595000");

        Map<String, String> headers = service.callbackHeaders(task, signed);

        assertThat(headers)
                .containsEntry("X-CPay-Signature", "base64-signature")
                .containsEntry("X-CPay-Signature-Version", "callback-v1")
                .containsEntry("X-CPay-Nonce", "nonce-value")
                .containsEntry("X-CPay-Timestamp", "1787595000")
                .containsEntry("X-CPay-Callback-Task-Id", "42")
                .containsEntry("X-CPay-Merchant-Id", "10003482")
                .containsEntry("X-CPay-Reference", "opfin-ref-123");
    }
}
