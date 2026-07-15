package net.citotech.cito.api.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.security.CanonicalRequestSigner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<PaymentResult> findExisting(String merchantNumber, String idempotencyKey, String body) {
        if (isBlank(idempotencyKey)) {
            return Optional.empty();
        }
        try {
            String sql = "SELECT request_hash, response_body FROM cpay_idempotency_keys "
                    + "WHERE merchant_number=:merchant_number AND idempotency_key=:idempotency_key "
                    + "ORDER BY id DESC LIMIT 1";
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("merchant_number", merchantNumber);
            parameters.addValue("idempotency_key", idempotencyKey.trim());
            List<StoredResponse> responses = jdbcTemplate.query(sql, parameters, (rs, rowNum) ->
                    new StoredResponse(rs.getString("request_hash"), rs.getString("response_body")));
            if (responses.isEmpty()) {
                return Optional.empty();
            }
            String requestHash = CanonicalRequestSigner.sha256Hex(body == null ? "" : body);
            StoredResponse response = responses.get(0);
            if (!requestHash.equals(response.requestHash)) {
                throw new PaymentGatewayException("Idempotency key was reused with a different request body");
            }
            return Optional.of(objectMapper.readValue(response.responseBody, PaymentResult.class));
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (DataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new PaymentGatewayException("Unable to read idempotency response");
        }
    }

    public void record(String merchantNumber, String idempotencyKey, String body, PaymentResult result) {
        if (isBlank(idempotencyKey)) {
            return;
        }
        try {
            String sql = "INSERT INTO cpay_idempotency_keys "
                    + "(merchant_number, idempotency_key, request_hash, response_body, status, created_at) "
                    + "VALUES (:merchant_number, :idempotency_key, :request_hash, :response_body, :status, CURRENT_TIMESTAMP)";
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("merchant_number", merchantNumber);
            parameters.addValue("idempotency_key", idempotencyKey.trim());
            parameters.addValue("request_hash", CanonicalRequestSigner.sha256Hex(body == null ? "" : body));
            parameters.addValue("response_body", objectMapper.writeValueAsString(result));
            parameters.addValue("status", result.getStatus());
            jdbcTemplate.update(sql, parameters);
        } catch (DataAccessException e) {
            // Idempotency remains backward compatible if the migration has not yet been enabled.
        } catch (Exception e) {
            throw new PaymentGatewayException("Unable to record idempotency response");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class StoredResponse {
        private final String requestHash;
        private final String responseBody;

        private StoredResponse(String requestHash, String responseBody) {
            this.requestHash = requestHash;
            this.responseBody = responseBody;
        }
    }
}

