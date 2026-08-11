package net.citotech.cito.communication.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers {@link CommunicationDeliveryAdminController} (B5 delivery-log visibility): the
 * per-merchant delivery log delegates to the repository, and the per-channel usage-watermark read
 * returns the underlying rows.
 */
class CommunicationDeliveryAdminControllerTest {

    @Test
    void deliveriesDelegatesToTheRepository() {
        DeliveryLogRepository repository = mock(DeliveryLogRepository.class);
        when(repository.listForMerchant(7L, 100))
                .thenReturn(
                        List.of(
                                new MessageDelivery(
                                        1L,
                                        7L,
                                        "SMS",
                                        "YO_SMS",
                                        null,
                                        null,
                                        "256700000001",
                                        DeliveryStatus.SENT,
                                        "trace",
                                        "ok",
                                        BigDecimal.ZERO,
                                        true)));

        Map<String, Object> body =
                new CommunicationDeliveryAdminController(
                                repository, mock(NamedParameterJdbcTemplate.class))
                        .deliveries(7L, 100);

        assertThat(body.get("code")).isEqualTo("000");
        assertThat((List<?>) body.get("deliveries")).hasSize(1);
        verify(repository).listForMerchant(7L, 100);
    }

    @Test
    void watermarksReturnsWatermarkRows() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "channel", "SMS",
                                        "last_delivery_id", 42L,
                                        "processed_flag", "Y")));

        Map<String, Object> body =
                new CommunicationDeliveryAdminController(
                                mock(DeliveryLogRepository.class), jdbcTemplate)
                        .watermarks();

        assertThat(body.get("code")).isEqualTo("000");
        assertThat((List<?>) body.get("watermarks")).hasSize(1);
    }
}
