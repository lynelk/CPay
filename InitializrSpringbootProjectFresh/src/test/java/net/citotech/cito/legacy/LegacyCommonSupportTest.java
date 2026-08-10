package net.citotech.cito.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class LegacyCommonSupportTest {
    private final LegacyCommonSupport support = new LegacyCommonSupport();

    @Test
    void jsonTextPreservesLegacyDefaultingSemantics() {
        JSONObject object = new JSONObject();
        object.put("text", "value");
        object.put("number", 12);
        object.put("missingLike", JSONObject.NULL);

        assertThat(support.jsonText(object, "text", "fallback")).isEqualTo("value");
        assertThat(support.jsonText(object, "number", "fallback")).isEqualTo("12");
        assertThat(support.jsonText(object, "missing", "fallback")).isEqualTo("fallback");
        assertThat(support.jsonText(object, "missingLike", "fallback")).isEqualTo("fallback");
    }

    @Test
    void numericTokenHasRequestedLengthAndDigitsOnly() {
        String token = support.randomNumericString(24);
        assertThat(token).hasSize(24).matches("[0-9]+$");
    }

    @Test
    void moneyUsesExplicitDecimalRounding() {
        assertThat(support.money("10.125", 2)).isEqualByComparingTo(new BigDecimal("10.13"));
    }

    @Test
    void invalidDecimalIsRejected() {
        assertThatThrownBy(() -> support.decimal("not-money"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid decimal value");
    }
}
