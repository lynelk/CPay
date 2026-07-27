package net.citotech.cito.crossborder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.FxQuoteRequest;
import net.citotech.cito.api.v2.dto.FxQuoteResponse;
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
