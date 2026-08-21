package net.citotech.cito.identity.domain;

/**
 * Execution backends for validation workloads (ISO domain mapping: identity/domain). An execution
 * backend schedules compute; it is never an evidence/identity source. {@code ARMADA} is the
 * Kubernetes batch scheduler, {@code LOCAL_SPRING} runs the check inside CPay.
 */
public enum ValidationExecutionBackendCode {
    LOCAL_SPRING,
    ARMADA
}
