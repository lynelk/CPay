package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

class RestExceptionHandlerTest {

    @Test
    void unexpectedExceptionsReturnStandardJsonErrorBody() {
        RestExceptionHandler handler = new RestExceptionHandler();

        ResponseEntity<String> response =
                handler.requestUnhandledException(new NullPointerException("missing setting"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        JSONObject body = new JSONObject(response.getBody());
        assertThat(body.getString("state")).isEqualTo("ERROR");
        assertThat(body.getString("code")).isEqualTo("102");
        assertThat(body.getString("message")).isEqualTo(GeneralException.ERRORS_102);
    }

    @Test
    void methodSecurityDenialsReturnExplicitForbiddenNot500() {
        RestExceptionHandler handler = new RestExceptionHandler();

        ResponseEntity<String> response =
                handler.requestAccessDenied(new AccessDeniedException("Admin role is required"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        JSONObject body = new JSONObject(response.getBody());
        assertThat(body.getString("state")).isEqualTo("ERROR");
        assertThat(body.getString("code")).isEqualTo("110");
        assertThat(body.getString("message")).isEqualTo(GeneralException.ERRORS_110);
    }
}
