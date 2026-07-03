package net.citotech.cito.api.v2.dto;

import javax.validation.constraints.NotBlank;

public class PaymentPartyRequest {
    @NotBlank
    private String type;

    @NotBlank
    private String value;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
