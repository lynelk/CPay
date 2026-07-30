package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.config.RequestCorrelationFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CommonOutboundHttpExecutorTest {

    @Test
    void delegatesHttpRequestsToTheSpringManagedExecutorAndPreservesCorrelationId() {
        AtomicReference<Map<String, String>> capturedHeaders = new AtomicReference<>();
        Common.setOutboundHttpExecutor(
                (method, url, data, headers) -> {
                    capturedHeaders.set(headers);
                    HttpRequestResponse response = new HttpRequestResponse();
                    response.setUrl(url);
                    response.setRequestData(data);
                    response.setRequestHeaders(headers);
                    response.setStatusCode(202);
                    response.setResponse("accepted");
                    response.setErrorMessage("");
                    return response;
                });
        MDC.put("request_id", "req-test-1");

        try {
            HttpRequestResponse response =
                    Common.doHttpRequest("POST", "https://provider.example/pay", "{}", Map.of());

            assertThat(response.getStatusCode()).isEqualTo(202);
            assertThat(response.getResponse()).isEqualTo("accepted");
            assertThat(capturedHeaders.get())
                    .containsEntry(RequestCorrelationFilter.REQUEST_ID_HEADER, "req-test-1");
        } finally {
            MDC.clear();
            Common.setOutboundHttpExecutor(null);
        }
    }
}
