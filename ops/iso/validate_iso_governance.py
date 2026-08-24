#!/usr/bin/env python3
"""Fail-closed consistency checks for the Cito ISO governance register.

This validates repository governance metadata, not ISO certification or the operating
effectiveness of manual/external controls.
"""
from __future__ import annotations

import json
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ROOT_RESOLVED = ROOT.resolve()
REGISTER = ROOT / "ops" / "iso" / "governance.json"

REQUIRED_STANDARDS = {
    "ISO-9001",
    "ISO-IEC-27001",
    "ISO-IEC-27000",
    "ISO-IEC-20000-1",
    "ISO-IEC-27032",
    "ISO-22301",
    "ISO-20022",
    "ISO-8583",
    "ISO-9362",
    "ISO-32212",
}
VALID_APPLICABILITY = {"APPLICABLE", "NOT_APPLICABLE", "CONDITIONAL"}
VALID_IMPLEMENTATION = {"IMPLEMENTED", "PARTIAL", "PLANNED", "NOT_APPLICABLE"}
VALID_RISK_STATUS = {"OPEN", "ACCEPTED", "CLOSED"}
VALID_RISK_TREATMENT = {"AVOID", "REDUCE", "TRANSFER", "ACCEPT"}
VALID_CLASSIFICATION = {"PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"}
VALID_SERVICE_TIERS = {"TIER_0", "TIER_1", "TIER_2", "TIER_3"}
CRITICAL_TIERS = {"TIER_0", "TIER_1"}
HIGH_RISK_DECISIONS = {"ACCEPTED", "BLOCKED_PENDING_ACCEPTANCE"}

errors: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def parse_date(value: str | None, context: str) -> date | None:
    if not value:
        fail(f"{context}: missing date")
        return None
    try:
        return date.fromisoformat(value)
    except ValueError:
        fail(f"{context}: invalid ISO date {value!r}")
        return None


def require_not_stale(value: str | None, context: str) -> None:
    parsed = parse_date(value, context)
    if parsed and parsed < date.today():
        fail(f"{context}: review date {parsed.isoformat()} is stale")


def require_future_due(value: str | None, context: str) -> None:
    parsed = parse_date(value, context)
    if parsed and parsed < date.today():
        fail(f"{context}: overdue since {parsed.isoformat()}")


def require_repo_file(raw: str, context: str) -> None:
    candidate = Path(raw)
    if candidate.is_absolute():
        fail(f"{context}: evidence path must be repository-relative: {raw}")
        return
    resolved = (ROOT / candidate).resolve()
    try:
        resolved.relative_to(ROOT_RESOLVED)
    except ValueError:
        fail(f"{context}: evidence path escapes repository: {raw}")
        return
    if not resolved.is_file():
        fail(f"{context}: evidence file does not exist or is not a file: {raw}")


def require_evidence(paths: list[str], context: str) -> None:
    require(bool(paths), f"{context}: evidence is required")
    for raw in paths:
        require_repo_file(raw, context)


def unique_ids(items: list[dict], context: str) -> set[str]:
    seen: set[str] = set()
    for item in items:
        item_id = item.get("id")
        require(bool(item_id), f"{context}: item missing id")
        if item_id in seen:
            fail(f"{context}: duplicate id {item_id}")
        if item_id:
            seen.add(item_id)
    return seen


try:
    data = json.loads(REGISTER.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError) as exc:
    print(f"ISO governance validation failed: cannot load {REGISTER}: {exc}", file=sys.stderr)
    raise SystemExit(1)

require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
programme = data.get("programme") or {}
require(bool(programme.get("name")), "programme.name is required")
require(bool(programme.get("scope")), "programme.scope is required")
require(bool(programme.get("ownerRole")), "programme.ownerRole is required")
require(bool(programme.get("executiveSponsorRole")), "programme.executiveSponsorRole is required")
require_not_stale(programme.get("nextReviewAt"), "programme")
status = programme.get("certificationStatus")
require(status in {"NOT_CERTIFIED", "CERTIFIED"}, "programme.certificationStatus invalid")
if status == "CERTIFIED":
    require(bool(programme.get("certificateEvidence")), "CERTIFIED status requires certificateEvidence")

standards = data.get("standards") or []
standard_ids = unique_ids(standards, "standards")
missing = REQUIRED_STANDARDS - standard_ids
extra = standard_ids - REQUIRED_STANDARDS
if missing:
    fail(f"standards: missing requested standards: {sorted(missing)}")
if extra:
    fail(f"standards: unexpected standards require deliberate validator update: {sorted(extra)}")
for std in standards:
    sid = std.get("id", "<unknown>")
    require(bool(std.get("edition")), f"{sid}: edition is required")
    require(bool(std.get("kind")), f"{sid}: kind is required")
    require(std.get("applicability") in VALID_APPLICABILITY, f"{sid}: invalid applicability")
    require(bool(std.get("ownerRole")), f"{sid}: ownerRole is required")
    require_not_stale(std.get("nextReviewAt"), sid)
    if std.get("applicability") == "CONDITIONAL":
        require(bool(std.get("note")), f"{sid}: conditional applicability requires note")

objectives = data.get("objectives") or []
unique_ids(objectives, "objectives")
for obj in objectives:
    oid = obj.get("id", "<unknown>")
    for field in ("domain", "name", "ownerRole", "metric", "target", "measurementSource", "reviewCadence"):
        require(bool(obj.get(field)), f"{oid}: {field} is required")
    require_not_stale(obj.get("nextReviewAt"), oid)

services = data.get("services") or []
service_ids = unique_ids(services, "services")
require(bool(services), "at least one service is required")
for svc in services:
    sid = svc.get("id", "<unknown>")
    require(bool(svc.get("name")), f"{sid}: name is required")
    require(bool(svc.get("ownerRole")), f"{sid}: ownerRole is required")
    tier = svc.get("tier")
    require(tier in VALID_SERVICE_TIERS, f"{sid}: tier must be one of {sorted(VALID_SERVICE_TIERS)}")
    require(svc.get("dataClassification") in VALID_CLASSIFICATION, f"{sid}: invalid dataClassification")
    require(isinstance(svc.get("dependencies"), list) and bool(svc.get("dependencies")), f"{sid}: dependencies required")
    require(bool(svc.get("slo")), f"{sid}: slo is required")
    if tier in CRITICAL_TIERS:
        require(isinstance(svc.get("rtoMinutes"), int) and svc["rtoMinutes"] > 0, f"{sid}: positive rtoMinutes required")
        require(isinstance(svc.get("rpoMinutes"), int) and svc["rpoMinutes"] >= 0, f"{sid}: non-negative rpoMinutes required")
        require(bool(svc.get("continuityProcedure")), f"{sid}: continuityProcedure required")
        require(isinstance(svc.get("customersUsers"), list) and bool(svc.get("customersUsers")), f"{sid}: customersUsers required for critical service")
        require(bool(svc.get("businessOutcome")), f"{sid}: businessOutcome required for critical service")
        require(bool(svc.get("supportHours")), f"{sid}: supportHours required for critical service")
        require(bool(svc.get("escalationPath")), f"{sid}: escalationPath required for critical service")
        mtd = svc.get("maximumTolerableDisruptionMinutes")
        require(isinstance(mtd, int) and mtd > 0, f"{sid}: positive maximumTolerableDisruptionMinutes required")
        if isinstance(mtd, int) and isinstance(svc.get("rtoMinutes"), int):
            require(mtd >= svc["rtoMinutes"], f"{sid}: maximum tolerable disruption cannot be shorter than RTO")
    for field in ("continuityProcedure", "monitoringEvidence"):
        raw = svc.get(field)
        if raw:
            require_repo_file(raw, f"{sid}.{field}")

risks = data.get("risks") or []
unique_ids(risks, "risks")
for risk in risks:
    rid = risk.get("id", "<unknown>")
    require(bool(risk.get("title")), f"{rid}: title required")
    require(bool(risk.get("ownerRole")), f"{rid}: ownerRole required")
    require(risk.get("status") in VALID_RISK_STATUS, f"{rid}: invalid status")
    require(risk.get("treatment") in VALID_RISK_TREATMENT, f"{rid}: invalid treatment")
    for field in ("inherentLikelihood", "inherentImpact", "residualLikelihood", "residualImpact"):
        value = risk.get(field)
        require(isinstance(value, int) and 1 <= value <= 5, f"{rid}: {field} must be 1..5")
    if risk.get("status") != "CLOSED":
        require(bool(risk.get("treatmentAction")), f"{rid}: open risk requires treatmentAction")
        require_future_due(risk.get("dueAt"), f"{rid}.dueAt")
        require_not_stale(risk.get("nextReviewAt"), rid)

    residual_score = 0
    if isinstance(risk.get("residualLikelihood"), int) and isinstance(risk.get("residualImpact"), int):
        residual_score = risk["residualLikelihood"] * risk["residualImpact"]

    if residual_score >= 10:
        decision = risk.get("highRiskDecision")
        require(decision in HIGH_RISK_DECISIONS, f"{rid}: high residual risk requires explicit acceptance or BLOCKED_PENDING_ACCEPTANCE")
        if decision == "ACCEPTED":
            require(bool(risk.get("acceptedByRole")), f"{rid}: accepted high risk requires acceptedByRole")
            parse_date(risk.get("acceptedAt"), f"{rid}.acceptedAt")
            require_not_stale(risk.get("acceptanceExpiresAt"), f"{rid}.acceptanceExpiresAt")
        elif decision == "BLOCKED_PENDING_ACCEPTANCE":
            require(bool(risk.get("acceptanceOwnerRole")), f"{rid}: pending high-risk acceptance requires acceptanceOwnerRole")
            require_future_due(risk.get("acceptanceDueAt"), f"{rid}.acceptanceDueAt")
            require(risk.get("productionContinuationAuthorized") is False, f"{rid}: pending high-risk acceptance must explicitly prohibit production continuation authorization")

    if risk.get("treatment") == "ACCEPT" or risk.get("status") == "ACCEPTED":
        require(bool(risk.get("acceptedByRole")), f"{rid}: accepted risk requires acceptedByRole")
        parse_date(risk.get("acceptedAt"), f"{rid}.acceptedAt")
        require_not_stale(risk.get("acceptanceExpiresAt"), f"{rid}.acceptanceExpiresAt")

controls = data.get("controls") or []
unique_ids(controls, "controls")
for ctl in controls:
    cid = ctl.get("id", "<unknown>")
    require(bool(ctl.get("domain")), f"{cid}: domain required")
    require(bool(ctl.get("ownerRole")), f"{cid}: ownerRole required")
    require(ctl.get("applicability") in VALID_APPLICABILITY, f"{cid}: invalid applicability")
    require(ctl.get("implementation") in VALID_IMPLEMENTATION, f"{cid}: invalid implementation")
    refs = ctl.get("standards") or []
    require(bool(refs), f"{cid}: at least one standards reference required")
    for ref in refs:
        if ref not in standard_ids:
            fail(f"{cid}: unknown standard reference {ref}")
    require_not_stale(ctl.get("nextReviewAt"), cid)
    evidence = ctl.get("evidence") or []
    if ctl.get("implementation") == "IMPLEMENTED":
        require_evidence(evidence, cid)
    else:
        for raw in evidence:
            require_repo_file(raw, cid)
    if ctl.get("implementation") in {"PARTIAL", "PLANNED"}:
        require(bool(ctl.get("action")), f"{cid}: {ctl.get('implementation')} requires action")
        require_future_due(ctl.get("dueAt"), f"{cid}.dueAt")
    if ctl.get("implementation") == "NOT_APPLICABLE":
        require(bool(ctl.get("rationale")), f"{cid}: NOT_APPLICABLE requires rationale")
        require(bool(ctl.get("approvedByRole")), f"{cid}: NOT_APPLICABLE requires approvedByRole")

suppliers = data.get("suppliers") or []
unique_ids(suppliers, "suppliers")
for supplier in suppliers:
    sid = supplier.get("id", "<unknown>")
    for field in ("name", "criticality", "ownerRole", "service", "reviewCadence"):
        require(bool(supplier.get(field)), f"{sid}: {field} required")
    require_not_stale(supplier.get("nextReviewAt"), sid)
    if supplier.get("service") != "ALL" and supplier.get("service") not in service_ids:
        fail(f"{sid}: unknown service {supplier.get('service')}")
    if supplier.get("criticality") == "CRITICAL":
        required = supplier.get("requiredEvidence") or []
        require(len(required) >= 3, f"{sid}: critical supplier requires explicit evidence categories")

required_docs = [
    "Docs/ISO/README.md",
    "Docs/ISO/Integrated-management-system-manual.md",
    "Docs/ISO/standards-applicability-matrix.md",
    "Docs/ISO/isms-risk-and-control-framework.md",
    "Docs/ISO/service-continuity-and-cybersecurity.md",
    "Docs/ISO/financial-messaging-interoperability.md",
    "Docs/ISO/net-zero-transition-planning.md",
    "Docs/ISO/internal-audit-management-review.md",
]
for raw in required_docs:
    require_repo_file(raw, "required IMS document")

if errors:
    print("ISO governance validation FAILED", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print(
    "ISO governance validation passed: "
    f"{len(standards)} standards, {len(controls)} controls, {len(risks)} risks, "
    f"{len(services)} services, {len(suppliers)} suppliers, {len(objectives)} objectives."
)
