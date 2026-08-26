from pathlib import Path
import re
import textwrap

repair_workflow = Path('.github/workflows/fix-financial-integrity-20260826.yml').read_text()
start = repair_workflow.index('          from pathlib import Path\n')
end = repair_workflow.index('\n          PY', start)
program = textwrap.dedent(repair_workflow[start:end])
exec(compile(program, '<cpay-financial-integrity>', 'exec'), {'__name__': '__main__'})

root = Path('InitializrSpringbootProjectFresh/src/test/java/net/citotech/cito')
unit = root / 'billing/invoicing/BillingInvoiceServiceTest.java'
text = unit.read_text()
old_ctor = '''    private final BillingInvoiceService service =
            new BillingInvoiceService(
                    repository, completenessGateService, ledgerAccountTemplateService);'''
new_ctor = '''    private final net.citotech.cito.billing.integration.cpay.BillingPaymentFundingService fundingService =
            mock(net.citotech.cito.billing.integration.cpay.BillingPaymentFundingService.class);
    private final BillingInvoiceService service =
            new BillingInvoiceService(
                    repository, completenessGateService, ledgerAccountTemplateService, fundingService);'''
if old_ctor not in text:
    raise SystemExit('BillingInvoiceServiceTest constructor fixture not found')
text = text.replace(old_ctor, new_ctor, 1)

replay_pattern = re.compile(
    r'    @Test\n    void paymentReplayDoesNotPostTwice\(\) \{.*?\n    \}\n',
    re.S,
)
replay_replacement = '''    @Test
    void paymentReplayDoesNotPostTwice() {
        when(repository.findForUpdate(55L))
                .thenReturn(Optional.of(finalizedInvoice("1000", "180", "1180")));
        when(fundingService.claim(
                        7L,
                        "PAY-1",
                        "UGX",
                        new BigDecimal("100"),
                        false,
                        "INVOICE",
                        "55"))
                .thenReturn(
                        new net.citotech.cito.billing.integration.cpay.BillingPaymentFundingService.FundingClaim(
                                91L, 3L, "PAY-1", "UGX", new BigDecimal("100"), true));
        assertThat(service.applyPayment(55L, "PAY-1", new BigDecimal("100"), "ops")).isZero();
        verify(ledgerAccountTemplateService, never())
                .postInvoicePaymentFromMerchantCollection(
                        anyLong(), anyLong(), any(), any(), any(), any());
    }
'''
text, count = replay_pattern.subn(replay_replacement, text, count=1)
if count != 1:
    raise SystemExit('payment replay fixture not found')
unit.write_text(text)

constructor_pattern = re.compile(
    r'new BillingInvoiceService\(\s*invoiceRepository,\s*gateService,\s*ledgerAccountTemplateService\s*\)'
)
for rel in (
    'billing/export/BillingTraceChainServiceTestcontainersTest.java',
    'billing/invoicing/BillingInvoiceFinalizeWorkflowTestcontainersTest.java',
    'billing/invoicing/BillingPhase3ExitCriterionTestcontainersTest.java',
):
    path = root / rel
    body = path.read_text()
    body, count = constructor_pattern.subn(
        'new BillingInvoiceService(\n                invoiceRepository, gateService, ledgerAccountTemplateService,\n                new net.citotech.cito.billing.integration.cpay.BillingPaymentFundingService(jdbcTemplate))',
        body,
        count=1,
    )
    if count != 1:
        raise SystemExit(f'constructor fixture not found: {rel}')
    path.write_text(body)
