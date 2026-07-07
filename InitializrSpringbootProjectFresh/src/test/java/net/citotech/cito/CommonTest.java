package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

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
}