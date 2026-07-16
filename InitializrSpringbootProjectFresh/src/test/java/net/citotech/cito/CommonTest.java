package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CommonTest {

    @Test
    void jsonTextAcceptsNumericFieldsFromDataGridRequests() {
        JSONObject request = new JSONObject();
        request.put("pageSize", 50);
        request.put("currentPage", 2);

        assertThat(Common.jsonText(request, "pageSize", ""))
            .isEqualTo("50");
        assertThat(Common.jsonText(request, "currentPage", ""))
            .isEqualTo("2");
    }

    @Test
    void jsonTextUsesDefaultForMissingOrNullValues() {
        JSONObject request = new JSONObject();
        request.put("currentPage", JSONObject.NULL);

        assertThat(Common.jsonText(request, "pageSize", "0"))
            .isEqualTo("0");
        assertThat(Common.jsonText(request, "currentPage", "0"))
            .isEqualTo("0");
    }

    @Test
    void generateSha256StringReturnsFullWidthHash() {
        assertThat(Common.generateSha256String("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
            .hasSize(64);
    }

    @Test
    void getIpAddressUsesFirstForwardedHopOnly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.4, 10.0.0.8");
        request.setRemoteAddr("10.0.0.9");

        assertThat(Common.getIpAddress(request)).isEqualTo("203.0.113.4");
    }

    @Test
    void getIpAddressFallsBackToRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");

        assertThat(Common.getIpAddress(request)).isEqualTo("10.0.0.9");
    }
}
