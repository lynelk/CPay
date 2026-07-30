package net.citotech.cito.loadtest;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.Map;

/**
 * Audit K5 (load-testing setup). Exercises the signed, payment-adjacent read endpoint
 * {@code GET /api/v2/balances} ({@link net.citotech.cito.api.v2.PaymentsV2Controller#balances})
 * using real CPay v2 request-signing headers built by {@link V2SignedRequestSupport}, rather than
 * only hitting an unauthenticated endpoint like {@link HealthCheckSimulation} does.
 *
 * <p>Without a real merchant seeded in the target database whose stored public key matches the
 * private key {@link V2SignedRequestSupport} signs with, the backend will correctly reject every
 * request here with 401 - that is expected and is itself proof the signature-verification gate is
 * being exercised (see {@code V2RequestSecurityService}), just against an environment with no
 * merchant/keypair provisioned to succeed against. To exercise the success path (200 with a real
 * balance list) in a real environment: seed a merchant, point
 * {@code -Dcpay.loadtest.privateKeyPath} at the matching PKCS8 PEM private key, and pass that
 * merchant's number via {@code -Dcpay.loadtest.merchantNumber}.
 *
 * <p>Kept deliberately lenient (accepts 200 OR 401) so the simulation is meaningfully runnable in
 * both an unseeded sandbox and a properly-seeded environment without needing two separate
 * simulation classes; a 5xx or connection failure is still a genuine failure either way.
 *
 * <p>Like {@link HealthCheckSimulation}, this is opt-in only (not part of {@code mvn test}/
 * {@code mvn verify}). It has been verified by actually executing {@code mvn gatling:test} here:
 * Gatling ran the full 3-users/sec-for-30s injection profile (90 requests, each with a freshly
 * signed set of headers built by {@link V2SignedRequestSupport}), and every request failed with a
 * plain {@code ConnectException: Connection refused} - expected, since nothing is listening on
 * {@code baseUrl} in this sandbox. That confirms the signing helper and the per-request header
 * wiring both work correctly; only a live target (and, for the 200 success path, a seeded
 * merchant/keypair) is missing here. Run with:
 * <pre>
 *   mvn gatling:test -Dgatling.simulationClass=net.citotech.cito.loadtest.BalanceCheckSimulation \
 *       -Dcpay.loadtest.baseUrl=http://localhost:8081 \
 *       -Dcpay.loadtest.merchantNumber=YOUR_MERCHANT_NUMBER \
 *       -Dcpay.loadtest.privateKeyPath=/path/to/merchant-private-key.pem
 * </pre>
 */
public class BalanceCheckSimulation extends Simulation {

    private final String baseUrl = System.getProperty("cpay.loadtest.baseUrl", "http://localhost:8081");
    private final String merchantNumber = System.getProperty("cpay.loadtest.merchantNumber", "LOADTEST-MERCHANT");
    private static final String PATH = "/api/v2/balances";

    private final HttpProtocolBuilder httpProtocol = http
        .baseUrl(baseUrl)
        .acceptHeader("application/json")
        .userAgentHeader("cpay-gatling-loadtest/1.0");

    private final ScenarioBuilder balanceCheck = scenario("Signed balance check")
        .exec(session -> {
            Map<String, String> headers = V2SignedRequestSupport.getRequestHeaders(
                merchantNumber, PATH, "merchantNumber", merchantNumber);
            return session.set("signatureHeaders", headers);
        })
        .exec(
            http("GET /api/v2/balances")
                .get(PATH + "?merchantNumber=" + merchantNumber)
                .header("X-CPay-Merchant-Number", session -> signatureHeader(session, "X-CPay-Merchant-Number"))
                .header("X-CPay-Signature-Version", session -> signatureHeader(session, "X-CPay-Signature-Version"))
                .header("X-CPay-Timestamp", session -> signatureHeader(session, "X-CPay-Timestamp"))
                .header("X-CPay-Nonce", session -> signatureHeader(session, "X-CPay-Nonce"))
                .header("X-CPay-Signature", session -> signatureHeader(session, "X-CPay-Signature"))
                .check(status().in(200, 401))
        );

    private static String signatureHeader(io.gatling.javaapi.core.Session session, String name) {
        Map<String, String> headers = session.getMap("signatureHeaders");
        return headers.get(name);
    }

    {
        setUp(
            balanceCheck.injectOpen(
                constantUsersPerSec(3).during(Duration.ofSeconds(30))
            )
        ).protocols(httpProtocol);
    }
}
