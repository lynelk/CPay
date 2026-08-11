package net.citotech.cito.communication.delivery;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * B5 delivery-log visibility surface (ISO domain mapping: communication/delivery): per-merchant
 * message-delivery log rows and the per-channel usage watermark state. The delivery log is the same
 * V53 table the usage relay reads, so this controller is the audit view an operator uses to confirm
 * a sent message was metered (delivery row SENT + billed_flag Y + watermark advanced).
 */
@RestController
@RequestMapping(path = "/api/v2/admin/communication/deliveries")
@PreAuthorize("hasRole('ADMIN')")
public class CommunicationDeliveryAdminController {

    private final DeliveryLogRepository deliveryLogRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CommunicationDeliveryAdminController(
            DeliveryLogRepository deliveryLogRepository, NamedParameterJdbcTemplate jdbcTemplate) {
        this.deliveryLogRepository = deliveryLogRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Map<String, Object> deliveries(
            @RequestParam long merchantId,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<MessageDelivery> rows = deliveryLogRepository.listForMerchant(merchantId, limit);
        return Map.of("code", "000", "deliveries", rows);
    }

    @GetMapping(path = "/watermarks")
    public Map<String, Object> watermarks() {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT channel, last_delivery_id, processed_flag, updated_at"
                                + " FROM communication_usage_watermark ORDER BY channel ASC",
                        new MapSqlParameterSource());
        return Map.of("code", "000", "watermarks", rows);
    }
}
