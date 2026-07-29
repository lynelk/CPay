package net.citotech.cito.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit C1: SafariComPaymentGateway.checkStatusResponseStorage() used to write a
 * plaintext ConversationID.json file to local disk that Api.getPayoutConversationIdToken read
 * back to resolve a Safaricom TransactionStatusQuery callback's ConversationID to our own
 * transaction reference. A per-instance local file can't be authoritative once there is more
 * than one app instance, so this store replaces it with the same DB-backed pattern already used
 * for provider tokens.
 */
@SuppressWarnings("unchecked")
class ProviderConversationReferenceStoreServiceTest {

    @Test
    void savesTheConversationIdToReferenceMapping() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ProviderConversationReferenceStoreService service = new ProviderConversationReferenceStoreService(jdbcTemplate);
        when(jdbcTemplate.update(contains("INSERT INTO provider_conversation_references"), any(MapSqlParameterSource.class)))
            .thenReturn(1);

        service.save("safaricom_mpesa", "AG_20260729_conv123", "TX-REF-1");

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(contains("INSERT INTO provider_conversation_references"), captor.capture());
        assertThat(captor.getValue().getValue("provider_code")).isEqualTo("safaricom_mpesa");
        assertThat(captor.getValue().getValue("conversation_id")).isEqualTo("AG_20260729_conv123");
        assertThat(captor.getValue().getValue("tx_reference")).isEqualTo("TX-REF-1");
    }

    @Test
    void rejectsBlankFields() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ProviderConversationReferenceStoreService service = new ProviderConversationReferenceStoreService(jdbcTemplate);

        assertThatThrownBy(() -> service.save("safaricom_mpesa", "", "TX-REF-1"))
            .isInstanceOf(PaymentGatewayException.class);
        assertThatThrownBy(() -> service.save("safaricom_mpesa", "conv123", null))
            .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void findReturnsTheStoredReferenceWhenPresent() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ProviderConversationReferenceStoreService service = new ProviderConversationReferenceStoreService(jdbcTemplate);
        when(jdbcTemplate.query(contains("SELECT tx_reference"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of("TX-REF-1"));

        Optional<String> found = service.find("safaricom_mpesa", "AG_20260729_conv123");

        assertThat(found).contains("TX-REF-1");
    }

    @Test
    void findReturnsEmptyWhenNoRowMatches() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ProviderConversationReferenceStoreService service = new ProviderConversationReferenceStoreService(jdbcTemplate);
        when(jdbcTemplate.query(contains("SELECT tx_reference"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());

        Optional<String> found = service.find("safaricom_mpesa", "unknown-conv-id");

        assertThat(found).isEmpty();
    }

    @Test
    void deleteRemovesTheMapping() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ProviderConversationReferenceStoreService service = new ProviderConversationReferenceStoreService(jdbcTemplate);

        service.delete("safaricom_mpesa", "AG_20260729_conv123");

        verify(jdbcTemplate).update(contains("DELETE FROM provider_conversation_references"), any(MapSqlParameterSource.class));
    }

    @Test
    void registryFailsOpenWhenNeverWired() {
        new ProviderConversationReferenceStoreRegistry(null);

        assertThat(ProviderConversationReferenceStoreRegistry.find("safaricom_mpesa", "conv-id")).isEmpty();
        ProviderConversationReferenceStoreRegistry.save("safaricom_mpesa", "conv-id", "ref");
        ProviderConversationReferenceStoreRegistry.delete("safaricom_mpesa", "conv-id");
    }

    @Test
    void registryDelegatesToTheWiredService() {
        ProviderConversationReferenceStoreService store = mock(ProviderConversationReferenceStoreService.class);
        when(store.find(anyString(), anyString())).thenReturn(Optional.of("TX-REF-9"));
        new ProviderConversationReferenceStoreRegistry(store);

        Optional<String> found = ProviderConversationReferenceStoreRegistry.find("safaricom_mpesa", "conv-id");
        ProviderConversationReferenceStoreRegistry.save("safaricom_mpesa", "conv-id", "TX-REF-9");
        ProviderConversationReferenceStoreRegistry.delete("safaricom_mpesa", "conv-id");

        assertThat(found).contains("TX-REF-9");
        verify(store).save("safaricom_mpesa", "conv-id", "TX-REF-9");
        verify(store).delete("safaricom_mpesa", "conv-id");

        new ProviderConversationReferenceStoreRegistry(null);
    }
}
