package net.citotech.cito.communication.ussd;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Setting;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provider-facing USSD session endpoint protected by a shared provider token. */
@RestController
@RequestMapping(path = "/api/v2/communication/ussd")
public class UssdSessionController {
    private final UssdSessionService sessionService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UssdSessionController(
            UssdSessionService sessionService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.sessionService = sessionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping(path = "/session", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> session(
            @RequestHeader(value = "X-CPay-USSD-Token", required = false) String token,
            @RequestBody UssdSessionRequest request) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).body("END Unauthorized");
        }
        try {
            return ResponseEntity.ok(sessionService.process(request).providerText());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("END Invalid request");
        }
    }

    private boolean authorized(String supplied) {
        Setting configured = Common.getSettings("ussd_provider_token", jdbcTemplate);
        String expected = configured == null ? null : configured.getSetting_value();
        if (expected == null || expected.isBlank() || supplied == null || supplied.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }
}
