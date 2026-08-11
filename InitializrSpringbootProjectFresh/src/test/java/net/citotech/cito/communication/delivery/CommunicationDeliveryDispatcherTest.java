package net.citotech.cito.communication.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.citotech.cito.communication.delivery.CommunicationDeliveryDispatcher.DeliveryOutcome;
import net.citotech.cito.communication.email.EmailDeliveryService;
import net.citotech.cito.communication.email.EmailSendRequest;
import net.citotech.cito.communication.email.EmailSendResult;
import net.citotech.cito.communication.sms.SmsGatewayAdapter;
import net.citotech.cito.communication.sms.SmsSendRequest;
import net.citotech.cito.communication.sms.SmsSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the B5a dispatch seam: SMS sends go through the injected SMS adapter, EMAIL through the
 * email service, and WHATSAPP/USSD fail closed (REJECTED, refundable) because no B4 adapter exists
 * yet — so the usage relay can never meter a message that was never delivered.
 */
class CommunicationDeliveryDispatcherTest {

    private DeliveryLogRepository deliveryLogRepository;
    private SmsGatewayAdapter smsGateway;
    private EmailDeliveryService emailDeliveryService;
    private CommunicationDeliveryDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        deliveryLogRepository = mock(DeliveryLogRepository.class);
        smsGateway = mock(SmsGatewayAdapter.class);
        emailDeliveryService = mock(EmailDeliveryService.class);
        when(deliveryLogRepository.insert(
                        any(Long.class),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(Long.class),
                        anyString()))
                .thenReturn(99L);
        dispatcher =
                new CommunicationDeliveryDispatcher(
                        deliveryLogRepository, smsGateway, emailDeliveryService);
    }

    @Test
    void smsSendRoutesThroughSmsGatewayAndRecordsSent() {
        when(smsGateway.send(any(SmsSendRequest.class)))
                .thenReturn(SmsSendResult.sent("trace", "ok"));
        when(deliveryLogRepository.updateStatus(99L, DeliveryStatus.SENT, "trace", "ok"))
                .thenReturn(1);

        DeliveryOutcome outcome =
                dispatcher.dispatch(7L, "SMS", "256700000001", null, "Hello", "YO_SMS", 1L);

        assertThat(outcome.status()).isEqualTo(DeliveryStatus.SENT);
        verify(smsGateway).send(any(SmsSendRequest.class));
        verify(emailDeliveryService, never()).send(any(EmailSendRequest.class));
    }

    @Test
    void emailSendRoutesThroughEmailService() {
        when(emailDeliveryService.send(any(EmailSendRequest.class)))
                .thenReturn(EmailSendResult.sent("smtp", ""));
        when(deliveryLogRepository.updateStatus(99L, DeliveryStatus.SENT, "smtp", ""))
                .thenReturn(1);

        DeliveryOutcome outcome =
                dispatcher.dispatch(7L, "EMAIL", "ops@example.com", "Subj", "Body", null, 2L);

        assertThat(outcome.status()).isEqualTo(DeliveryStatus.SENT);
        verify(emailDeliveryService).send(any(EmailSendRequest.class));
        verify(smsGateway, never()).send(any(SmsSendRequest.class));
    }

    @Test
    void whatsappFailsClosedUntilAnAdapterExists() {
        DeliveryOutcome outcome =
                dispatcher.dispatch(7L, "WHATSAPP", "256700000001", null, "Hi", null, 3L);

        assertThat(outcome.status()).isEqualTo(DeliveryStatus.REJECTED);
        assertThat(outcome.status().isRefundable()).isTrue();
        verify(smsGateway, never()).send(any(SmsSendRequest.class));
        verify(emailDeliveryService, never()).send(any(EmailSendRequest.class));
    }

    @Test
    void adapterThrowRecordsFailedInsteadOfAborting() {
        when(smsGateway.send(any(SmsSendRequest.class)))
                .thenThrow(new IllegalStateException("provider down"));

        DeliveryOutcome outcome =
                dispatcher.dispatch(7L, "SMS", "256700000001", null, "Hello", "YO_SMS", 4L);

        assertThat(outcome.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(outcome.status().isRefundable()).isTrue();
    }
}
