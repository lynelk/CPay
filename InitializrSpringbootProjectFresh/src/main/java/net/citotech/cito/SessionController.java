package net.citotech.cito;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.Model.User;
import net.citotech.cito.Model.UserPrivilege;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author josephtabajjwa
 */
@Controller
public class SessionController {
    /*@GetMapping("/")
    public String process(Model model, HttpSession session) {
        @SuppressWarnings("unchecked")
        List<String> messages = (List<String>) session.getAttribute("MY_SESSION_MESSAGES");

        if (messages == null) {
                messages = new ArrayList<>();
        }
        model.addAttribute("sessionMessages", messages);

        return "index";
    }*/

    @GetMapping(
            path = "/api/v2/session/me",
            produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> currentSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authenticated", false, "code", "SESSION_REQUIRED"));
        }
        Object adminUser = session.getAttribute("user");
        if (adminUser instanceof User user) {
            return ResponseEntity.ok(adminSession(user));
        }
        Object merchantUser = session.getAttribute("merchantUser");
        if (merchantUser instanceof MerchantUser user) {
            return ResponseEntity.ok(merchantSession(user));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("authenticated", false, "code", "SESSION_REQUIRED"));
    }

    @PostMapping("/persistMessage")
    public String persistMessage(@RequestParam("msg") String msg, HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        List<String> messages =
                (List<String>) request.getSession().getAttribute("MY_SESSION_MESSAGES");
        if (messages == null) {
            messages = new ArrayList<>();
            request.getSession().setAttribute("MY_SESSION_MESSAGES", messages);
        }

        messages.add(msg);
        request.getSession().setAttribute("MY_SESSION_MESSAGES", messages);
        return "redirect:/";
    }

    @PostMapping("/destroy")
    public String destroySession(HttpServletRequest request) {
        request.getSession().invalidate();
        return "redirect:/";
    }

    private Map<String, Object> adminSession(User user) {
        Map<String, Object> body = baseSession(user, "ADMIN");
        body.put("userId", String.valueOf(user.getId()));
        body.put("displayName", user.getName());
        body.put("roles", List.of("ADMIN"));
        body.put("permissions", privileges(user.getPrivileges()));
        body.put("assuranceLevel", "PASSWORD");
        return body;
    }

    private Map<String, Object> merchantSession(MerchantUser user) {
        Map<String, Object> body = baseSession(user, "MERCHANT");
        body.put("userId", String.valueOf(user.getId()));
        body.put("merchantId", user.getMerchant_id());
        body.put("merchantNumber", user.getMerchant_number());
        body.put("merchantName", user.getMerchant_name());
        body.put("displayName", user.getName());
        body.put("roles", List.of(blank(user.getRole()) ? "OWNER" : user.getRole()));
        body.put("permissions", privileges(user.getPrivileges()));
        body.put("assuranceLevel", "PASSWORD");
        return body;
    }

    private Map<String, Object> baseSession(User user, String actorType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", true);
        body.put("actorType", actorType);
        body.put("tenantId", "default");
        body.put("environment", "local");
        body.put("email", user.getEmail());
        body.put("phone", user.getPhone());
        body.put("status", user.getStatus());
        return body;
    }

    private List<String> privileges(List<UserPrivilege> privileges) {
        if (privileges == null) {
            return List.of();
        }
        return privileges.stream()
                .map(UserPrivilege::getPrivilege)
                .filter(value -> !blank(value))
                .distinct()
                .toList();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
