package net.citotech.cito.api.v2.dto;

public class V2HealthResponse {
    private String status;
    private String version;

    public V2HealthResponse() {
    }

    public V2HealthResponse(String status, String version) {
        this.status = status;
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
