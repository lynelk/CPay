package net.citotech.cito.compliance;

public class RiskDecision {
    private final String decision;
    private final String reasonCode;
    private final String summary;

    public RiskDecision(String decision, String reasonCode, String summary) {
        this.decision = decision;
        this.reasonCode = reasonCode;
        this.summary = summary;
    }

    public static RiskDecision allow(String summary) {
        return new RiskDecision("ALLOW", "ALLOW", summary);
    }

    public static RiskDecision review(String reasonCode, String summary) {
        return new RiskDecision("REVIEW", reasonCode, summary);
    }

    public static RiskDecision block(String reasonCode, String summary) {
        return new RiskDecision("BLOCK", reasonCode, summary);
    }

    public String getDecision() {
        return decision;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isBlocked() {
        return "BLOCK".equalsIgnoreCase(decision);
    }
}
