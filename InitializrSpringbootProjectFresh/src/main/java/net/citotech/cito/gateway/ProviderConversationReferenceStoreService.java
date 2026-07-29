package net.citotech.cito.gateway;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Correlates a provider's async callback conversation/tracking id back to our own transaction
 * reference (audit item C1).
 *
 * SafariComPaymentGateway.checkStatus() issues a Safaricom TransactionStatusQuery for a
 * disbursement and registers this app's callback endpoint as the async ResultURL. Safaricom's
 * callback only carries the ConversationID it minted for that query - not our own reference -
 * so the callback handler needs a way to resolve ConversationID back to the reference it was
 * given when the query was submitted. This used to be a plaintext JSON file per ConversationID
 * under custom.lockfiledirectory; that doesn't survive across multiple app instances/pods and
 * duplicates the DB-backed pattern already used for provider tokens (see provider_tokens /
 * ProviderTokenStoreService). Conversation ids and tx references are not secrets (no PINs,
 * tokens, or credentials), so unlike provider tokens this store does not need encryption at rest.
 */
@Service
public class ProviderConversationReferenceStoreService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProviderConversationReferenceStoreService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void save(String providerCode, String conversationId, String txReference) {
        require(providerCode, "providerCode");
        require(conversationId, "conversationId");
        require(txReference, "txReference");

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", providerCode);
        p.addValue("conversation_id", conversationId);
        p.addValue("tx_reference", txReference);
        jdbcTemplate.update(
            "INSERT INTO provider_conversation_references "
                + "(provider_code, conversation_id, tx_reference) "
                + "VALUES (:provider_code, :conversation_id, :tx_reference) "
                + "ON DUPLICATE KEY UPDATE tx_reference=:tx_reference",
            p);
    }

    public Optional<String> find(String providerCode, String conversationId) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", providerCode);
        p.addValue("conversation_id", conversationId);
        List<String> refs = jdbcTemplate.query(
            "SELECT tx_reference FROM provider_conversation_references "
                + "WHERE provider_code=:provider_code AND conversation_id=:conversation_id LIMIT 1",
            p,
            (rs, rowNum) -> rs.getString("tx_reference"));
        return refs.isEmpty() ? Optional.empty() : Optional.of(refs.get(0));
    }

    @Transactional
    public void delete(String providerCode, String conversationId) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", providerCode);
        p.addValue("conversation_id", conversationId);
        jdbcTemplate.update(
            "DELETE FROM provider_conversation_references "
                + "WHERE provider_code=:provider_code AND conversation_id=:conversation_id",
            p);
    }

    private void require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new PaymentGatewayException(field + " is required");
        }
    }
}
