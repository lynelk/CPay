package net.citotech.cito.crossborder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.FxQuoteRequest;
import net.citotech.cito.api.v2.dto.FxQuoteResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class FxQuoteServiceTest {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void createsQuoteFromLatestActiveRate() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn((List) List.of(new BigDecimal("3.5000000000")));
        FxQuoteService service = new FxQuoteService(jdbcTemplate);

        FxQuoteResponse response = service.createQuote(request(), merchant());

        assertThat(response.getSourceCurrency()).isEqualTo("UGX");
        assertThat(response.getTargetCurrency()).isEqualTo("KES");
        assertThat(response.getSourceAmount()).isEqualByComparingTo("1000.0000");
        assertThat(response.getTargetAmount()).isEqualByComparingTo("3500.0000");
        assertThat(response.getQuoteReference()).startsWith("FX-");
        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void findActiveQuoteReturnsMatchingUnexpiredQuote() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        Instant expiresAt = Instant.now().plusSeconds(300);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn((List) List.of(new FxQuoteRecord(
                "FX-1", 12L, "UGX", "KES", new BigDecimal("1000.00"), new BigDecimal("3500.00"),
                new BigDecimal("3.5"), expiresAt)));
        FxQuoteService service = new FxQuoteService(jdbcTemplate);

        FxQuoteRecord record = service.findActiveQuote("FX-1", 12L);

        assertThat(record.quoteReference()).isEqualTo("FX-1");
        assertThat(record.targetAmount()).isEqualByComparingTo("3500.00");
    }

    @Test
    void findActiveQuoteRejectsBlankReference() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        FxQuoteService service = new FxQuoteService(jdbcTemplate);

        assertThatThrownBy(() -> service.findActiveQuote("  ", 12L))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("quoteReference is required");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void findActiveQuoteRejectsExpiredOrAlreadyBoundQuote() {
        // The SQL filters on quote_status='ACTIVE' AND expires_at > CURRENT_TIMESTAMP, so an
        // expired or already-bound (single-use) quote simply returns no rows - the service must
        // not silently treat that as a fresh quote.
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn((List) List.of());
        FxQuoteService service = new FxQuoteService(jdbcTemplate);

        assertThatThrownBy(() -> service.findActiveQuote("FX-1", 12L))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("expired or was not found");
    }

    private FxQuoteRequest request() {
        FxQuoteRequest request = new FxQuoteRequest();
        request.setMerchantNumber("1000003");
        request.setSourceCurrency("UGX");
        request.setTargetCurrency("KES");
        request.setSourceAmount("1000");
        return request;
    }

    private Merchant merchant() {
        Merchant merchant = new Merchant();
        merchant.setId(12L);
        merchant.setAccount_number("1000003");
        merchant.setStatus("ACTIVE");
        return merchant;
    }
}
