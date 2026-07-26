package net.citotech.cito.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ComplianceReportingControllerTest {

    @Test
    void upsertProfileRejectsMissingEntityIdWithoutThrowing() {
        ComplianceReportingService service = mock(ComplianceReportingService.class);
        ComplianceCaseService caseService = mock(ComplianceCaseService.class);
        ComplianceReportingController controller = new ComplianceReportingController(service, caseService);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entityType", "MERCHANT");

        ResponseEntity<?> response = controller.upsertProfile(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(caseService, never()).upsertProfile(
            anyString(), anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void upsertProfileRejectsNonNumericEntityIdWithoutThrowing() {
        ComplianceReportingService service = mock(ComplianceReportingService.class);
        ComplianceCaseService caseService = mock(ComplianceCaseService.class);
        ComplianceReportingController controller = new ComplianceReportingController(service, caseService);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entityId", "not-a-number");

        ResponseEntity<?> response = controller.upsertProfile(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(caseService, never()).upsertProfile(
            anyString(), anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void upsertProfileAcceptsNumericEntityId() {
        ComplianceReportingService service = mock(ComplianceReportingService.class);
        ComplianceCaseService caseService = mock(ComplianceCaseService.class);
        when(caseService.upsertProfile(
            anyString(), anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
            .thenReturn(1);
        ComplianceReportingController controller = new ComplianceReportingController(service, caseService);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entityId", 42);

        ResponseEntity<?> response = controller.upsertProfile(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(caseService).upsertProfile(
            eq("MERCHANT"), eq(42L), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
    }
}
