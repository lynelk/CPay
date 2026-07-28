package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.citotech.cito.config.RequestCorrelationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Covers audit H2: outbound HTTP calls (provider APIs, webhook/callback deliveries) must forward
 * the current request's correlation id, so our logs and the receiving system's logs for the same
 * call can be cross-referenced - but must never override a header the caller explicitly set.
 */
class CommonHttpRequestCorrelationTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void forwardsTheCurrentRequestIdAsAHeader() throws Exception {
        MDC.put("request_id", "req-abc-123");
        AtomicReference<String> receivedHeader = new AtomicReference<>();
        HttpServer server = startCapturingServer(receivedHeader);
        try {
            Common.doHttpRequest("GET", "http://localhost:" + server.getAddress().getPort() + "/", "", new HashMap<>());
            assertThat(receivedHeader.get()).isEqualTo("req-abc-123");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void neverOverridesACallerSuppliedCorrelationHeader() throws Exception {
        MDC.put("request_id", "req-from-mdc");
        AtomicReference<String> receivedHeader = new AtomicReference<>();
        HttpServer server = startCapturingServer(receivedHeader);
        try {
            Map<String, String> callerHeaders = new HashMap<>();
            callerHeaders.put(RequestCorrelationFilter.REQUEST_ID_HEADER, "req-explicit");
            Common.doHttpRequest("GET", "http://localhost:" + server.getAddress().getPort() + "/", "", callerHeaders);
            assertThat(receivedHeader.get()).isEqualTo("req-explicit");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void omitsTheHeaderWhenThereIsNoCurrentRequestId() throws Exception {
        AtomicReference<String> receivedHeader = new AtomicReference<>();
        HttpServer server = startCapturingServer(receivedHeader);
        try {
            Common.doHttpRequest("GET", "http://localhost:" + server.getAddress().getPort() + "/", "", new HashMap<>());
            assertThat(receivedHeader.get()).isNull();
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startCapturingServer(AtomicReference<String> receivedHeader) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            receivedHeader.set(exchange.getRequestHeaders().getFirst(RequestCorrelationFilter.REQUEST_ID_HEADER));
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }
}
