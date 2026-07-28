package net.citotech.cito.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/auth", produces = "application/json")
public class CsrfController {
    @GetMapping(path = "/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("parameterName", token.getParameterName());
        response.put("headerName", token.getHeaderName());
        response.put("token", token.getToken());
        return response;
    }
}
