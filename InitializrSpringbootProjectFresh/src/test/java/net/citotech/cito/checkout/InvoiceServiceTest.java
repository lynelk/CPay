package net.citotech.cito.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.PaymentOrchestrationService;
import net.citotech.cito.api.v2.dto.HostedCheckoutPayRequest;
import net.citotech.cito.api.v2.dto.InvoiceCreateRequest;
import net.citotech.cito.api.v2.dto.InvoiceResponse;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.webhook.MerchantWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit N9 (invoicing / request-to-pay objects): DRAFT/SENT/PAID/EXPIRED/CANCELLED status
 * transitions and their guards, the unique (merchant_id, reference) constraint path, and that
 * paying a SENT invoice - via the same {@code PaymentOrchestrationService.collect(...)} entry point
 * checkout's payment links already use - marks it PAID and stores the transaction id.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class InvoiceServiceTest {

    @Test
    void createRejectsMismatchedMerchantNumber() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PaymentOrchestrationService orchestrationService = mock(PaymentOrchestrationService.class);
        InvoiceService service =
                new InvoiceService(
                        jdbcTemplate,
                        orchestrationService,
                        mock(MerchantWebhookService.class),
                        "https://cpay.example");

        assertThatThrownBy(() -> service.create(createRequest("OTHER"), merchant()))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("does not match");

        verifyNoInteractions(jdbcTemplate, orchestrationService);
    }

    @Test
    void createInsertsADraftInvoiceAndReturnsAOneTimePayUrl() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(
                        contains("INSERT INTO invoices"), any(MapSqlParameterSource.class)))
                .thenReturn(1);
        stubInvoiceLookupByReference(
                jdbcTemplate,
                "INV-1",
                invoiceRow(1L, 42L, "INV-1", "5000", "UGX", "DRAFT", null, null, null));

        InvoiceService service =
                new InvoiceService(
                        jdbcTemplate,
                        mock(PaymentOrchestrationService.class),
                        mock(MerchantWebhookService.class),
                        "https://cpay.example");
        InvoiceCreateRequest request = createRequest("M100");
        request.setReference("INV-1");

        InvoiceResponse response = service.create(request, merchant());

        assertThat(response.getStatus()).isEqualTo("DRAFT");
        assertThat(response.getReference()).isEqualTo("INV-1");
        assertThat(response.getPayUrl()).startsWith("https://cpay.example/api/v2/invoices/pay/");
        verify(jdbcTemplate)
                .update(
                        contains("INSERT INTO invoices"),
                        argThat(
                                (MapSqlParameterSource p) ->
                                        Objects.equals(42L, p.getValue("merchant_id"))
                                                && "INV-1".equals(p.getValue("reference"))));
    }

    @Test
    void createRejectsADuplicateReferenceForTheSameMerchant() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(
                        contains("INSERT INTO invoices"), any(MapSqlParameterSource.class)))
                .thenThrow(new DuplicateKeyException("uk_invoice_merchant_reference"));

        InvoiceService service =
                new InvoiceService(
                        jdbcTemplate,
                        mock(PaymentOrchestrationService.class),
                        mock(MerchantWebhookService.class),
                        "https://cpay.example");
        InvoiceCreateRequest request = createRequest("M100");
        request.setReference("INV-DUP");

        assertThatThrownBy(() -> service.create(request, merchant()))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void sendTransitionsDraftToSent() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubInvoiceLookupByReference(
                jdbcTemplate,
                "INV-1",
                invoiceRow(1L, 42L, "INV-1", "5000", "UGX", "DRAFT", null, null, null));
        when(jdbcTemplate.update(
                        contains("SET status=:new_status"),
                        argThat(
                                (MapSqlParameterSource p) ->
                                        "SENT".equals(p.getValue("new_status"))
                                                && "DRAFT".equals(p.getValue("expected_status")))))
                .thenReturn(1);

        InvoiceService service =
                new InvoiceService(
                        jdbcTemplate,
                        mock(PaymentOrchestrationService.class),
                        mock(MerchantWebhookService.class),
                        "https://cpay.example");
        InvoiceResponse response = service.send("M100", "INV-1", merchant());

        assertThat(response.getStatus()).isEqualTo("SENT");
    }

    @Test
    void sendQueuesAnInvoiceIssuedWebhook() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubInvoiceLookupByReference(
                jdbcTemplate,
                "INV-1",
                invoiceRow(1L, 42L, "INV-1", "5000", "UGX", "DRAFT", null, null, null));
        when(jdbcTemplate.update(
                        contains("SET status=:new_status"),
                        argThat(
                                (MapSqlParameterSource p) ->
                                        "SENT".equals(p.getValue("new_status"))
                                                && "DRAFT".equals(p.getValue("expected_status")))))
                .thenReturn(1);
        MerchantWebhookService webhookService = mock(MerchantWebhookService.class);

        InvoiceService service =
                new InvoiceService(
                        jdbcTemplate,
                        mock(PaymentOrchestrationService.class),
                        webhookService,
                        "https://cpay.example");
        service.send("M100", "INV-1", merchant());

        verify(webhookService)
                .enqueue(
                        eq(42L),
                        eq("invoice.issued"),
                        eq("INV-1"),
                        argThat(
                                (String payload) ->
                                        payload.contains("\"invoiceId\":\"INV-1\"")
                                                && payload.contains("\"status\":\"SENT\"")));
    }

    @Test
    void sendStillReturnsSentWhenTheWebhookEnqueueThrows() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubInvoiceLookupByReference(
                jdbcTemplate,
                "INV-1",
                invoiceRow(1L, 42L, "INV-1", "5000", "UGX", "DRAFT", null, null, null));
        when(jdbcTemplate.update(
                        contains("SET status=:new_status"),
                        argThat(
                                (MapSqlParameterSource p) ->
                                        "SENT".equals(p.getValue("new_status"))
                                                && "DRAFT".equals(p.getValue("expected_status")))))
                .thenReturn(1);
        MerchantWebhookService webhookService = mock(MerchantWebhookService.class);
        when(webhookService.enqueue(anyLong(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        InvoiceService service =
                new InvoiceService(
                        jdbcTemplate,
                        mock(PaymentOrchestrationService.class),
                        webhookService,
                        "https://cpay.example");
        InvoiceResponse response = service.send("M100", "INV-1", merchant());

        assertThat(response.getStatus()).isEqualTo("SENT");
    }

    @Test
    void sendRejectsANonDraftInvoice() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubInvoiceLookupByReference(
                jdbcTemplate,
                "INV-1",
                invoiceRow(1L, 42L, "INV-1", "5000", "UGX", "SENT", null, null, null));

        InvoiceService service =
                new InvoiceService(
                        jdbcTemplate,
                        mock(PaymentOrchestrationService.class),
                        mock(MerchantWebhookService.class),
                        "https://cpay.example");

        assertThatThrownBy(() -> service.send("M100", "INV-1", merchant()))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Only a DRAFT invoice can be sent");
        verify(jdbcTemplate, times(0))
                .update(contains("SET status=:new_status"), any(MapSqlParameterSource.class));
    }

    @Test
    void cancelTransitionsSentToCancelled() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubInvoiceLookupByReference(
                jdbcTemplate,
                "INV-1",
                invoiceRow(1L, 42L, "INV-1", "5000", "UGX", "SENT", null, null, null));
        when(jdbcTemplate.update(
                        contains("SET status=:new_status"),
                        argThat(
                                (MapSqlParameterSource p) ->
                                        "CANCELLED".equals(p.getValue("new_status"))
                                                && "SENT".equals(p.getValue("expected_status")))))
                .thenReturn(1);

        InvoiceService service =
                new InvoiceService(
                        jdbcTemplate,
                        mock(PaymentOrchestrationService.class),
                        mock(MerchantWebhookService.class),
                        "https://cpay.example");
        InvoiceResponse response = service.cancel("M100", "INV-1", merchant());

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelRejectsAnAlreadyPaidInvoice() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubInvoiceLookupByReference(
                jdbcTemplate,
                "INV-1",
                invoiceRow(1L, 42L, "INV-1", "5000", "UGX", "PAID", null, null, "tx-999"));

        InvoiceService service =
                new InvoiceService(
                        jdbcTemplate,
                        mock(PaymentOrchestrationService.class),
                        mock(MerchantWebhookService.class),
                        "https://cpay.example");

        assertThatThrownBy(() -> service.cancel("M100", "INV-1", merchant()))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Cannot cancel")
                .hasMessageContaining("PAID");
        verify(jdbcTemplate, times(0))
                .update(contains("SET status=:new_status"), any(MapSqlParameterSource.class));
    }

    @Test
    void statusForMapsSuccessfulResultToPaid() {
        InvoiceService service =
                new InvoiceService(
                        mock(NamedParameterJdbcTemplate.class),
                        mock(PaymentOrchestrationService.class),
                        mock(MerchantWebhookService.class),
                        "https://cpay.example");
        PaymentResult result = new PaymentResult();
        result.setStatus("SUCCESSFUL");

        assertThat(service.statusFor(result)).isEqualTo("PAID");
    }

    @Test
    void statusForKeepsANonSuccessfulResultAsSent() {
        InvoiceService service =
                new InvoiceService(
                        mock(NamedParameterJdbcTemplate.class),
                        mock(PaymentOrchestrationService.class),
                        mock(MerchantWebhookService.class),
                        "https://cpay.example");
        PaymentResult submitted = new PaymentResult();
        submitted.setStatus("SUBMITTED");
        PaymentResult failed = new PaymentResult();
        failed.setStatus("FAILED");

        assertThat(service.statusFor(submitted)).isEqualTo("SENT");
        assertThat(service.statusFor(failed)).isEqualTo("SENT");
    }

    @Test
    void payRejectsAnInvoiceThatHasNotBeenSentYet() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubInvoiceLookupByToken(
                jdbcTemplate,
                "tok-1",
                invoiceRow(1L, 42L, "INV-1", "5000", "UGX", "DRAFT", null, null, null));
        PaymentOrchestrationService orchestrationService = mock(PaymentOrchestrationService.class);

        InvoiceService service =
                new InvoiceService(
                        jdbcTemplate,
                        orchestrationService,
                        mock(MerchantWebhookService.class),
                        "https://cpay.example");

        assertThatThrownBy(() -> service.pay("tok-1", payRequest("256700000000"), "127.0.0.1"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not been sent yet");
        verifyNoInteractions(orchestrationService);
    }

    @Test
    void payRejectsAnAlreadyPaidInvoice() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubInvoiceLookupByToken(
                jdbcTemplate,
                "tok-1",
                invoiceRow(1L, 42L, "INV-1", "5000", "UGX", "PAID", null, null, "tx-1"));
        PaymentOrchestrationService orchestrationService = mock(PaymentOrchestrationService.class);

        InvoiceService service =
                new InvoiceService(
                        jdbcTemplate,
                        orchestrationService,
                        mock(MerchantWebhookService.class),
                        "https://cpay.example");

        assertThatThrownBy(() -> service.pay("tok-1", payRequest("256700000000"), "127.0.0.1"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("already been paid");
        verifyNoInteractions(orchestrationService);
    }

    @Test
    void payFlipsAnOverdueSentInvoiceToExpiredAndRefuses() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        Date yesterday = Date.valueOf(LocalDate.now().minusDays(1));
        stubInvoiceLookupByToken(
                jdbcTemplate,
                "tok-1",
                invoiceRow(1L, 42L, "INV-1", "5000", "UGX", "SENT", yesterday, null, null));
        when(jdbcTemplate.update(
                        contains("SET status=:new_status"),
                        argThat(
                                (MapSqlParameterSource p) ->
                                        "EXPIRED".equals(p.getValue("new_status"))
                                                && "SENT".equals(p.getValue("expected_status")))))
                .thenReturn(1);
        PaymentOrchestrationService orchestrationService = mock(PaymentOrchestrationService.class);

        InvoiceService service =
                new InvoiceService(
                        jdbcTemplate,
                        orchestrationService,
                        mock(MerchantWebhookService.class),
                        "https://cpay.example");

        assertThatThrownBy(() -> service.pay("tok-1", payRequest("256700000000"), "127.0.0.1"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("expired");
        verifyNoInteractions(orchestrationService);
        verify(jdbcTemplate)
                .update(
                        contains("SET status=:new_status"),
                        argThat(
                                (MapSqlParameterSource p) ->
                                        "EXPIRED".equals(p.getValue("new_status"))));
    }

    @Test
    void payingASentInvoiceMarksItPaidAndStoresTheTransactionId() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubInvoiceLookupByToken(
                jdbcTemplate,
                "tok-1",
                invoiceRow(7L, 42L, "INV-1", "5000", "UGX", "SENT", null, null, null));
        stubMerchantLookupById(jdbcTemplate, 42L, "M100");
        when(jdbcTemplate.update(
                        contains("status='PROCESSING'"),
                        argThat((MapSqlParameterSource p) -> Objects.equals(7L, p.getValue("id")))))
                .thenReturn(1);

        PaymentOrchestrationService orchestrationService = mock(PaymentOrchestrationService.class);
        PaymentResult collectResult = new PaymentResult();
        collectResult.setStatus("SUCCESSFUL");
        collectResult.setTransactionId("tx-abc-123");
        when(orchestrationService.collect(any(), any(), anyString())).thenReturn(collectResult);

        InvoiceService service =
                new InvoiceService(
                        jdbcTemplate,
                        orchestrationService,
                        mock(MerchantWebhookService.class),
                        "https://cpay.example");

        PaymentResult result = service.pay("tok-1", payRequest("256700000000"), "127.0.0.1");

        assertThat(result.getTransactionId()).isEqualTo("tx-abc-123");
        verify(jdbcTemplate)
                .update(
                        contains("status='PAID', created_transaction_id=:tx_id"),
                        argThat(
                                (MapSqlParameterSource p) ->
                                        Objects.equals(7L, p.getValue("id"))
                                                && "tx-abc-123".equals(p.getValue("tx_id"))));
    }

    @Test
    void payLeavesAnInvoiceSentWhenCollectionIsNotYetSuccessful() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubInvoiceLookupByToken(
                jdbcTemplate,
                "tok-1",
                invoiceRow(7L, 42L, "INV-1", "5000", "UGX", "SENT", null, null, null));
        stubMerchantLookupById(jdbcTemplate, 42L, "M100");
        when(jdbcTemplate.update(contains("status='PROCESSING'"), any(MapSqlParameterSource.class)))
                .thenReturn(1);

        PaymentOrchestrationService orchestrationService = mock(PaymentOrchestrationService.class);
        PaymentResult collectResult = new PaymentResult();
        collectResult.setStatus("SUBMITTED");
        collectResult.setTransactionId("tx-pending");
        when(orchestrationService.collect(any(), any(), anyString())).thenReturn(collectResult);

        InvoiceService service =
                new InvoiceService(
                        jdbcTemplate,
                        orchestrationService,
                        mock(MerchantWebhookService.class),
                        "https://cpay.example");
        service.pay("tok-1", payRequest("256700000000"), "127.0.0.1");

        verify(jdbcTemplate)
                .update(
                        contains("SET status='SENT' WHERE id=:id AND status='PROCESSING'"),
                        argThat((MapSqlParameterSource p) -> Objects.equals(7L, p.getValue("id"))));
        verify(jdbcTemplate, times(0))
                .update(contains("created_transaction_id"), any(MapSqlParameterSource.class));
    }

    private InvoiceCreateRequest createRequest(String merchantNumber) {
        InvoiceCreateRequest request = new InvoiceCreateRequest();
        request.setMerchantNumber(merchantNumber);
        request.setAmount("5000");
        request.setCurrency("UGX");
        request.setDescription("July invoice");
        return request;
    }

    private HostedCheckoutPayRequest payRequest(String payerAccount) {
        HostedCheckoutPayRequest request = new HostedCheckoutPayRequest();
        request.setPayerAccount(payerAccount);
        return request;
    }

    private Merchant merchant() {
        Merchant merchant = new Merchant();
        merchant.setId(42L);
        merchant.setAccount_number("M100");
        merchant.setStatus("ACTIVE");
        return merchant;
    }

    private void stubInvoiceLookupByReference(
            NamedParameterJdbcTemplate jdbcTemplate, String reference, ResultSet row) {
        when(jdbcTemplate.query(
                        contains("WHERE merchant_id=:merchant_id AND reference=:reference"),
                        argThat(
                                (MapSqlParameterSource p) ->
                                        p != null && reference.equals(p.getValue("reference"))),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(row, 1));
                        });
    }

    private void stubInvoiceLookupByToken(
            NamedParameterJdbcTemplate jdbcTemplate, String token, ResultSet row) {
        when(jdbcTemplate.query(
                        contains("WHERE public_token_hash=:hash"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(row, 1));
                        });
    }

    /**
     * Stubs Common.getMerchantById(...) and the merchant-admins lookup it triggers internally
     * (empty).
     */
    private void stubMerchantLookupById(
            NamedParameterJdbcTemplate jdbcTemplate, long merchantId, String accountNumber) {
        ResultSet merchantRow = mock(ResultSet.class);
        try {
            when(merchantRow.getString("name")).thenReturn("Test Merchant");
            when(merchantRow.getString("short_name")).thenReturn("Test");
            when(merchantRow.getString("account_number")).thenReturn(accountNumber);
            when(merchantRow.getString("status")).thenReturn("ACTIVE");
            when(merchantRow.getLong("id")).thenReturn(merchantId);
            when(merchantRow.getString("created_on")).thenReturn(null);
            when(merchantRow.getString("created_by")).thenReturn(null);
            when(merchantRow.getString("account_type")).thenReturn("STANDARD");
            when(merchantRow.getString("public_key")).thenReturn(null);
            when(merchantRow.getString("private_key")).thenReturn(null);
            when(merchantRow.getString("hmac_secret")).thenReturn(null);
            when(merchantRow.getString("allowed_apis")).thenReturn("");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(jdbcTemplate.query(
                        contains("FROM merchants"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(merchantRow, 1));
                        });
        when(jdbcTemplate.query(
                        contains("merchant_admins"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenReturn(List.of());
    }

    private ResultSet invoiceRow(
            long id,
            long merchantId,
            String reference,
            String amount,
            String currency,
            String status,
            Date dueDate,
            String payerContact,
            String createdTransactionId) {
        ResultSet row = mock(ResultSet.class);
        try {
            when(row.getLong("id")).thenReturn(id);
            when(row.getLong("merchant_id")).thenReturn(merchantId);
            when(row.getString("reference")).thenReturn(reference);
            when(row.getBigDecimal("amount")).thenReturn(new BigDecimal(amount));
            when(row.getString("currency")).thenReturn(currency);
            when(row.getString("description")).thenReturn("July invoice");
            when(row.getString("payer_name")).thenReturn(null);
            when(row.getString("payer_contact")).thenReturn(payerContact);
            when(row.getDate("due_date")).thenReturn(dueDate);
            when(row.getString("status")).thenReturn(status);
            when(row.getString("public_token_hash")).thenReturn("hash-placeholder");
            when(row.getString("created_transaction_id")).thenReturn(createdTransactionId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return row;
    }
}
