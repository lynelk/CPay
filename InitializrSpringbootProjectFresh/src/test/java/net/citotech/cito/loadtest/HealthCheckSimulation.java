package net.citotech.cito.loadtest;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;

/**
 * Audit K5 (load-testing setup). Baseline smoke simulation against the unauthenticated
 * {@code GET /api/v2/health} endpoint ({@link net.citotech.cito.api.v2.V2HealthController}) - the
 * simplest possible real request, used to establish that the load-testing toolchain itself works
 * end to end (JVM, network, assertions) before layering the more realistic, signed
 * {@link BalanceCheckSimulation} on top.
 *
 * <p>This does NOT run as part of {@code mvn test}/{@code mvn verify} - Gatling's Maven plugin has
 * no {@code <executions>} binding in {@code pom.xml}, and Surefire only picks up
 * {@code *Test}/{@code *Tests}/{@code *TestCase} classes, which this deliberately is not. Run it
 * against a real running instance of the app with:
 * <pre>
 *   mvn gatling:test -Dgatling.simulationClass=net.citotech.cito.loadtest.HealthCheckSimulation \
 *       -Dcpay.loadtest.baseUrl=http://localhost:8081
 * </pre>
 * No environment in this development sandbox has the app actually running against a real
 * database, so this has been verified by actually executing {@code mvn gatling:test} here: Gatling
 * discovered the simulation, ran the full 5-users/sec-for-30s injection profile (150 requests),
 * and generated its usual HTML report - every request failed with a plain
 * {@code ConnectException: Connection refused}, exactly as expected with nothing listening on
 * {@code baseUrl}. That confirms the toolchain itself (class discovery, injection scheduling, HTTP
 * client, checks, reporting) is wired correctly; only an actual live target is missing here.
 */
public class HealthCheckSimulation extends Simulation {

    private final String baseUrl = System.getProperty("cpay.loadtest.baseUrl", "http://localhost:8081");

    private final HttpProtocolBuilder httpProtocol = http
        .baseUrl(baseUrl)
        .acceptHeader("application/json")
        .userAgentHeader("cpay-gatling-loadtest/1.0");

    private final ScenarioBuilder healthCheck = scenario("Health check baseline")
        .exec(
            http("GET /api/v2/health")
                .get("/api/v2/health")
                .check(status().is(200))
        );

    {
        setUp(
            healthCheck.injectOpen(
                constantUsersPerSec(5).during(Duration.ofSeconds(30))
            )
        ).protocols(httpProtocol);
    }
}
