package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.Model.User;
import net.citotech.cito.Model.UserPrivilege;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class SessionControllerTest {

    private final SessionController controller = new SessionController();

    @Test
    void currentSessionReturnsUnauthorizedWhenNoPortalSessionExists() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<Map<String, Object>> response = controller.currentSession(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("authenticated", false);
    }

    @Test
    void currentSessionReturnsAdminBootstrapPayload() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        User admin = new User();
        admin.setId(7L);
        admin.setName("Admin User");
        admin.setEmail("admin@coresynergi.es");
        admin.setStatus("ACTIVE");
        admin.setPrivileges(List.of(privilege("ledger.read")));
        request.getSession(true).setAttribute("user", admin);

        ResponseEntity<Map<String, Object>> response = controller.currentSession(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("authenticated", true)
                .containsEntry("actorType", "ADMIN")
                .containsEntry("userId", "7")
                .containsEntry("email", "admin@coresynergi.es");
        assertThat(response.getBody().get("permissions")).asList().contains("ledger.read");
    }

    @Test
    void currentSessionReturnsMerchantBootstrapPayload() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MerchantUser merchant = new MerchantUser();
        merchant.setId(8L);
        merchant.setName("Merchant User");
        merchant.setEmail("merchant@coresynergi.es");
        merchant.setStatus("ACTIVE");
        merchant.setMerchant_id(99L);
        merchant.setMerchant_number("M-99");
        merchant.setMerchant_name("Merchant Ltd");
        merchant.setRole("FINANCE");
        merchant.setPrivileges(List.of(privilege("payout.create")));
        request.getSession(true).setAttribute("merchantUser", merchant);

        ResponseEntity<Map<String, Object>> response = controller.currentSession(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("authenticated", true)
                .containsEntry("actorType", "MERCHANT")
                .containsEntry("merchantId", 99L)
                .containsEntry("merchantNumber", "M-99");
        assertThat(response.getBody().get("roles")).asList().contains("FINANCE");
        assertThat(response.getBody().get("permissions")).asList().contains("payout.create");
    }

    private UserPrivilege privilege(String value) {
        UserPrivilege privilege = new UserPrivilege();
        privilege.setPrivilege(value);
        return privilege;
    }
}
