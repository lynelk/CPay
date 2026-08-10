#!/usr/bin/env python3
"""Deterministic, idempotent source rewrite for the legacy god-class extraction track.

The repository connector replaces whole files, which is awkward for 3k/6k-line legacy classes.
This script performs named-method rewrites using a small Java-aware brace scanner. It deliberately
fails when an expected anchor is absent, so source drift cannot silently produce a half-refactor.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "InitializrSpringbootProjectFresh" / "src" / "main" / "java" / "net" / "citotech" / "cito"
CONTROLLER = BACKEND / "TransactionsLogController.java"
COMMON = BACKEND / "Common.java"


def method_span(source: str, marker: str) -> tuple[int, int]:
    start = source.find(marker)
    if start < 0:
        raise RuntimeError(f"Expected marker not found: {marker}")
    brace = source.find("{", start)
    if brace < 0:
        raise RuntimeError(f"Opening brace not found after: {marker}")

    depth = 0
    i = brace
    state = "code"
    while i < len(source):
        ch = source[i]
        nxt = source[i + 1] if i + 1 < len(source) else ""
        if state == "code":
            if ch == '"':
                state = "string"
            elif ch == "'":
                state = "char"
            elif ch == "/" and nxt == "/":
                state = "line_comment"
                i += 1
            elif ch == "/" and nxt == "*":
                state = "block_comment"
                i += 1
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return start, i + 1
        elif state == "string":
            if ch == "\\":
                i += 1
            elif ch == '"':
                state = "code"
        elif state == "char":
            if ch == "\\":
                i += 1
            elif ch == "'":
                state = "code"
        elif state == "line_comment":
            if ch == "\n":
                state = "code"
        elif state == "block_comment":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 1
        i += 1
    raise RuntimeError(f"Unbalanced braces after: {marker}")


def replace_method(source: str, marker: str, replacement: str) -> str:
    start, end = method_span(source, marker)
    return source[:start] + replacement.rstrip() + source[end:]


def rewrite_controller() -> None:
    source = CONTROLLER.read_text()
    field_anchor = (
        "    @Autowired\n"
        "    private net.citotech.cito.ledger.LegacyLedgerPostingService legacyLedgerPostingService;\n"
    )
    query_field = (
        "\n    @Autowired\n"
        "    private net.citotech.cito.transactions.TransactionQueryService transactionQueryService;\n"
    )
    if query_field.strip() not in source:
        if field_anchor not in source:
            raise RuntimeError("TransactionsLogController dependency anchor changed")
        source = source.replace(field_anchor, field_anchor + query_field, 1)

    admin_method = '''@PostMapping(path = "/getTransactions")
    public String getTransactions(
            @RequestBody String requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        HttpSession session = request.getSession();
        if (session.getAttribute("user") == null) {
            return GeneralException.getError("107", GeneralException.ERRORS_107);
        }
        User sessionUser = (User) session.getAttribute("user");
        if (!Common.isUserAllowedAccessToThis("ACCESS_TRANSACTION_LOG", sessionUser)) {
            return GeneralException.getError("110", GeneralException.ERRORS_110);
        }
        return transactionQueryService.adminTransactions(requestBody);
    }'''
    source = replace_method(source, '@PostMapping(path = "/getTransactions")', admin_method)

    merchant_method = '''@PostMapping(path = "/getMerchantTransactions")
    public String getMerchantTransactions(
            @RequestBody String requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        HttpSession session = request.getSession();
        if (session.getAttribute("merchantUser") == null) {
            return GeneralException.getError("107", GeneralException.ERRORS_107);
        }
        MerchantUser sessionUser = (MerchantUser) session.getAttribute("merchantUser");
        if (!Common.isUserAllowedAccessToThis("ACCESS_TRANSACTION_LOG", sessionUser)) {
            return GeneralException.getError("110", GeneralException.ERRORS_110);
        }
        return transactionQueryService.merchantTransactions(requestBody, sessionUser);
    }'''
    source = replace_method(
        source, '@PostMapping(path = "/getMerchantTransactions")', merchant_method
    )
    CONTROLLER.write_text(source)


def rewrite_common_pure_helpers() -> None:
    source = COMMON.read_text()
    support_field = (
        "    private static final net.citotech.cito.legacy.LegacyCommonSupport LEGACY_SUPPORT =\n"
        "            new net.citotech.cito.legacy.LegacyCommonSupport();\n\n"
    )
    anchor = "public class Common {\n"
    if "LegacyCommonSupport LEGACY_SUPPORT" not in source:
        if anchor not in source:
            raise RuntimeError("Common class declaration changed")
        source = source.replace(anchor, anchor + support_field, 1)

    source = replace_method(
        source,
        "public static String jsonText(",
        '''public static String jsonText(JSONObject obj, String key, String defaultValue) {
        return LEGACY_SUPPORT.jsonText(obj, key, defaultValue);
    }''',
    )
    source = replace_method(
        source,
        "public static String randomNumericString(",
        '''public static String randomNumericString(int count) {
        return LEGACY_SUPPORT.randomNumericString(count);
    }''',
    )
    source = replace_method(
        source,
        "public static String randomAlphaNumericString(",
        '''public static String randomAlphaNumericString(int count) {
        return LEGACY_SUPPORT.randomAlphaNumericString(count);
    }''',
    )
    source = replace_method(
        source,
        "public static String urlEncodeValue(",
        '''public static String urlEncodeValue(String value) {
        return LEGACY_SUPPORT.urlEncodeValue(value);
    }''',
    )
    source = replace_method(
        source,
        "public static double round(",
        '''public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        return java.math.BigDecimal.valueOf(value)
                .setScale(places, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }''',
    )
    COMMON.write_text(source)


def main() -> None:
    rewrite_controller()
    rewrite_common_pure_helpers()
    print("God-class extraction delegates applied.")


if __name__ == "__main__":
    main()
