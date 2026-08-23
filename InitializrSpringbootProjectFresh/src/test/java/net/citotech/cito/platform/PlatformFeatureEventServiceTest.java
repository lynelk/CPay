package net.citotech.cito.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.marketplace.MarketplaceSplitService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PlatformFeatureEventServiceTest {

    @Test
    void acceptedPendingPaymentLeavesSplitEventRecoverable() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class)))
                .thenReturn(1);
        PlatformFeatureEventService service =
                new PlatformFeatureEventService(
                        jdbcTemplate,
                        mock(MarketplaceSplitService.class),
                        mock(CitoFeatureAccessService.class),
                        new ObjectMapper());
        PaymentResult result = new PaymentResult();
        result.setStatus("PENDING");

        service.confirmPaymentOutcome("PFE-123", result);

        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("status")).isEqualTo("WAITING_PAYMENT");
        assertThat(parameters.getValue().getValue("last_error").toString())
                .contains("waiting for final transaction status");
    }
}
