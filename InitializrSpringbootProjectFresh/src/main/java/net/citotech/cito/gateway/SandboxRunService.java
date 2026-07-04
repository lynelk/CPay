package net.citotech.cito.gateway;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SandboxRunService {
    private final PaymentChannelRegistry registry;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SandboxRunService(PaymentChannelRegistry registry, NamedParameterJdbcTemplate jdbcTemplate) {
        this.registry = registry;
        this.jdbcTemplate = jdbcTemplate;
    }

    public long run(String channelCode, String scenarioName, String account, String amount) {
        PaymentChannelAdapter adapter = registry.findByChannelCode(channelCode).orElseThrow(() -> new PaymentGatewayException("Unsupported channel: " + channelCode));
        PaymentGatewayRequest request = new PaymentGatewayRequest("SANDBOX", account, Double.valueOf(amount), "SANDBOX-REF", "Sandbox validation", "sandbox-callback", null);
        String response = adapter.collect(request).toString();
        return save(adapter.displayName(), adapter.channelCode(), scenarioName, request.getAccountIdentifier(), response, "PASSED");
    }

    private long save(String providerCode, String channelCode, String scenarioName, String requestSummary, String responseSummary, String status) {
        String sql = "INSERT INTO provider_sandbox_runs (provider_code, channel_code, scenario_name, run_status, request_summary, response_summary) VALUES (:provider_code, :channel_code, :scenario_name, :status, :request_summary, :response_summary)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", providerCode);
        p.addValue("channel_code", channelCode);
        p.addValue("scenario_name", scenarioName);
        p.addValue("status", status);
        p.addValue("request_summary", requestSummary);
        p.addValue("response_summary", responseSummary);
        jdbcTemplate.update(sql, p);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
        return id != null ? id : 0L;
    }
}

