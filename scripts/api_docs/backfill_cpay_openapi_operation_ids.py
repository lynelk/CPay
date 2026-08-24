#!/usr/bin/env python3
"""Backfill the known missing CPay OpenAPI operationIds without reserializing the contract.

This is intentionally fail-closed and exact-path based. It only inserts an operationId immediately
after the summary of one of the explicitly approved method/path pairs below. Any missing route,
method, summary, conflicting operationId, or duplicate resulting operationId aborts the patch.
"""
from __future__ import annotations

from pathlib import Path

SPEC = Path("Docs/Api/cpay-v2-openapi.yaml")
METHODS = {"get", "post", "put", "patch", "delete", "options", "head", "trace"}

TARGETS: list[tuple[str, str, str]] = [
    ("post", "/api/v2/admin/compliance/cases", "createComplianceCase"),
    ("get", "/api/v2/admin/finance-operations/settlements/{id}", "getFinanceSettlementBatch"),
    ("post", "/api/v2/admin/finance-operations/settlements/{id}/items", "addFinanceSettlementItem"),
    ("post", "/api/v2/admin/finance-operations/settlements/{id}/transition", "transitionFinanceSettlementBatch"),
    ("get", "/api/v2/admin/finance-operations/treasury/positions", "listFinanceTreasuryPositions"),
    ("post", "/api/v2/admin/finance-operations/treasury/positions", "recordFinanceTreasuryPosition"),
    ("get", "/api/v2/admin/finance-operations/reconciliation/exceptions", "listFinanceReconciliationExceptions"),
    ("post", "/api/v2/admin/finance-operations/reconciliation/exceptions", "createFinanceReconciliationException"),
    ("post", "/api/v2/admin/finance-operations/reconciliation/exceptions/{id}/resolve", "resolveFinanceReconciliationException"),
    ("post", "/api/v2/admin/finance-operations/daily-close", "openFinanceDailyClose"),
    ("get", "/api/v2/admin/finance-operations/daily-close/{businessDate}", "getFinanceDailyClose"),
    ("post", "/api/v2/admin/finance-operations/daily-close/{id}/decision", "decideFinanceDailyClose"),
    ("get", "/api/v2/admin/finance-operations/reports/exports", "listFinanceReportExports"),
    ("post", "/api/v2/admin/finance-operations/reports/exports", "requestFinanceReportExport"),
    ("get", "/api/v2/admin/finance-operations/incidents", "listFinanceIncidents"),
    ("post", "/api/v2/admin/finance-operations/incidents", "createFinanceIncident"),
    ("post", "/api/v2/admin/finance-operations/incidents/{id}/events", "addFinanceIncidentEvent"),
    ("get", "/api/v2/product-experience/merchant/{merchantId}/onboarding", "getMerchantOnboarding"),
    ("post", "/api/v2/product-experience/merchant/{merchantId}/onboarding", "upsertMerchantOnboarding"),
    ("post", "/api/v2/product-experience/merchant/{merchantId}/onboarding/steps", "upsertMerchantOnboardingStep"),
    ("get", "/api/v2/product-experience/developer/applications", "listDeveloperApplications"),
    ("post", "/api/v2/product-experience/developer/applications", "createDeveloperApplication"),
    ("post", "/api/v2/product-experience/developer/applications/{applicationId}/api-keys", "createDeveloperApiKey"),
    ("get", "/api/v2/product-experience/payment-links", "listProductPaymentLinks"),
    ("post", "/api/v2/product-experience/payment-links", "createProductPaymentLink"),
    ("post", "/api/v2/product-experience/checkout/sessions", "createCheckoutSession"),
    ("get", "/api/v2/product-experience/invoices", "listProductInvoices"),
    ("post", "/api/v2/product-experience/invoices", "createProductInvoice"),
    ("post", "/api/v2/product-experience/invoices/{invoiceId}/line-items", "addProductInvoiceLineItem"),
    ("get", "/api/v2/product-experience/channel-journeys", "listChannelJourneys"),
    ("post", "/api/v2/product-experience/channel-journeys", "upsertChannelJourney"),
    ("get", "/api/v2/product-experience/dashboard/widgets", "listDashboardWidgets"),
    ("post", "/api/v2/product-experience/dashboard/widgets", "upsertDashboardWidget"),
    ("get", "/api/v2/product-experience/sandbox-guides", "listSandboxGuides"),
    ("post", "/api/v2/product-experience/sandbox-guides", "upsertSandboxGuide"),
    ("get", "/api/v2/product-experience/go-live/{merchantId}", "getGoLiveReadiness"),
    ("post", "/api/v2/product-experience/go-live/{merchantId}", "upsertGoLiveReadiness"),
    ("post", "/api/v2/product-experience/go-live/{merchantId}/items", "upsertGoLiveReadinessItem"),
    ("get", "/api/v2/admin/kyb/profiles", "listKybProfiles"),
    ("post", "/api/v2/admin/kyb/profiles", "upsertKybProfile"),
    ("post", "/api/v2/admin/kyb/profiles/{merchantNumber}/decision", "decideKybProfile"),
    ("get", "/api/v2/admin/compliance/cases/{caseReference}", "getComplianceCase"),
    ("post", "/api/v2/admin/compliance/cases/{caseReference}/decision", "decideComplianceCaseByReference"),
    ("post", "/api/v2/admin/compliance/screening-results", "recordComplianceScreeningResult"),
    ("post", "/api/v2/admin/compliance/monitoring-alerts", "createComplianceMonitoringAlert"),
    ("post", "/api/v2/admin/regulatory/evidence-exports", "createRegulatoryEvidenceExport"),
    ("get", "/api/v2/cross-border/corridors", "listCrossBorderCorridors"),
    ("post", "/api/v2/admin/cross-border/corridors", "upsertCrossBorderCorridor"),
    ("post", "/api/v2/admin/cross-border/corridor-routes", "upsertCrossBorderCorridorRoute"),
    ("post", "/api/v2/beneficiaries", "createBeneficiary"),
    ("get", "/api/v2/beneficiaries/{beneficiaryReference}", "getBeneficiary"),
    ("post", "/api/v2/beneficiaries/{beneficiaryReference}/instruments", "addBeneficiaryInstrument"),
    ("get", "/api/v2/fx/quotes/{quoteReference}", "getFxQuote"),
    ("get", "/api/v2/cross-border/transfers/{transferReference}", "getCrossBorderTransfer"),
    ("post", "/api/v2/admin/cross-border/transfers/{transferReference}/transition", "transitionCrossBorderTransfer"),
    ("post", "/api/v2/admin/cross-border/settlement-batches", "createCrossBorderSettlementBatch"),
    ("post", "/api/v2/admin/cross-border/treasury-exposure", "recordCrossBorderTreasuryExposure"),
    ("post", "/api/v2/admin/cross-border/reports", "createCrossBorderReport"),
]


def find_path_bounds(lines: list[str], route: str) -> tuple[int, int]:
    marker = f"  {route}:\n"
    try:
        start = lines.index(marker)
    except ValueError as exc:
        raise SystemExit(f"OpenAPI operationId backfill: route not found: {route}") from exc
    end = len(lines)
    for index in range(start + 1, len(lines)):
        if lines[index].startswith("  /") or lines[index] == "components:\n":
            end = index
            break
    return start, end


def find_method_bounds(lines: list[str], route: str, method: str) -> tuple[int, int]:
    path_start, path_end = find_path_bounds(lines, route)
    marker = f"    {method}:\n"
    try:
        start = lines.index(marker, path_start + 1, path_end)
    except ValueError as exc:
        raise SystemExit(f"OpenAPI operationId backfill: {method.upper()} not found for {route}") from exc
    end = path_end
    for index in range(start + 1, path_end):
        candidate = lines[index]
        if candidate.startswith("    ") and not candidate.startswith("      "):
            token = candidate.strip().removesuffix(":").lower()
            if token in METHODS:
                end = index
                break
    return start, end


def backfill_one(lines: list[str], method: str, route: str, operation_id: str) -> bool:
    start, end = find_method_bounds(lines, route, method)
    summary_index = None
    existing = None
    for index in range(start + 1, end):
        line = lines[index]
        if line.startswith("      summary:") and summary_index is None:
            summary_index = index
        if line.startswith("      operationId:"):
            existing = line.split(":", 1)[1].strip()
            break
    if summary_index is None:
        raise SystemExit(f"OpenAPI operationId backfill: summary missing for {method.upper()} {route}")
    if existing is not None:
        if existing != operation_id:
            raise SystemExit(
                f"OpenAPI operationId backfill: conflicting operationId for {method.upper()} {route}: "
                f"expected {operation_id}, found {existing}"
            )
        return False
    lines.insert(summary_index + 1, f"      operationId: {operation_id}\n")
    return True


def main() -> None:
    lines = SPEC.read_text(encoding="utf-8").splitlines(keepends=True)
    changed = 0
    for method, route, operation_id in TARGETS:
        changed += int(backfill_one(lines, method, route, operation_id))

    operation_ids: dict[str, int] = {}
    for line in lines:
        if line.startswith("      operationId:"):
            value = line.split(":", 1)[1].strip()
            operation_ids[value] = operation_ids.get(value, 0) + 1
    duplicates = sorted(value for value, count in operation_ids.items() if count > 1)
    if duplicates:
        raise SystemExit(f"OpenAPI operationId backfill: duplicate operationIds after patch: {duplicates}")

    for method, route, operation_id in TARGETS:
        start, end = find_method_bounds(lines, route, method)
        expected = f"      operationId: {operation_id}\n"
        if expected not in lines[start:end]:
            raise SystemExit(f"OpenAPI operationId backfill: verification failed for {method.upper()} {route}")

    SPEC.write_text("".join(lines), encoding="utf-8")
    print(f"OpenAPI operationId backfill: inserted {changed} approved operationId value(s)")


if __name__ == "__main__":
    main()
