#!/usr/bin/env python3
"""Deterministic, idempotent source rewrite for the legacy god-class extraction track."""
from pathlib import Path
import re

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
            if ch == '"': state = "string"
            elif ch == "'": state = "char"
            elif ch == "/" and nxt == "/": state = "line_comment"; i += 1
            elif ch == "/" and nxt == "*": state = "block_comment"; i += 1
            elif ch == "{": depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0: return start, i + 1
        elif state == "string":
            if ch == "\\": i += 1
            elif ch == '"': state = "code"
        elif state == "char":
            if ch == "\\": i += 1
            elif ch == "'": state = "code"
        elif state == "line_comment" and ch == "\n": state = "code"
        elif state == "block_comment" and ch == "*" and nxt == "/": state = "code"; i += 1
        i += 1
    raise RuntimeError(f"Unbalanced braces after: {marker}")


def replace_method(source: str, marker: str, replacement: str) -> str:
    start, end = method_span(source, marker)
    return source[:start] + replacement.rstrip() + source[end:]


def user_guard(method_name: str, mapping: str, permission: str, delegate: str) -> str:
    return f'''@PostMapping(path = "{mapping}")
    public String {method_name}(
            @RequestBody String requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {{
        HttpSession session = request.getSession();
        if (session.getAttribute("user") == null) {{
            return GeneralException.getError("107", GeneralException.ERRORS_107);
        }}
        User sessionUser = (User) session.getAttribute("user");
        if (!Common.isUserAllowedAccessToThis("{permission}", sessionUser)) {{
            return GeneralException.getError("110", GeneralException.ERRORS_110);
        }}
        return {delegate};
    }}'''


def merchant_guard(method_name: str, mapping: str, permission: str, delegate: str) -> str:
    return f'''@PostMapping(path = "{mapping}")
    public String {method_name}(
            @RequestBody String requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {{
        HttpSession session = request.getSession();
        if (session.getAttribute("merchantUser") == null) {{
            return GeneralException.getError("107", GeneralException.ERRORS_107);
        }}
        MerchantUser sessionUser = (MerchantUser) session.getAttribute("merchantUser");
        if (!Common.isUserAllowedAccessToThis("{permission}", sessionUser)) {{
            return GeneralException.getError("110", GeneralException.ERRORS_110);
        }}
        return {delegate};
    }}'''


def rewrite_controller() -> None:
    source = CONTROLLER.read_text()
    field_anchor = "    private net.citotech.cito.transactions.TransactionQueryService transactionQueryService;\n"
    resolution_field = (
        "\n    @Autowired\n"
        "    private net.citotech.cito.transactions.TransactionResolutionService transactionResolutionService;\n"
    )
    if "TransactionResolutionService transactionResolutionService" not in source:
        if field_anchor not in source: raise RuntimeError("Query-service dependency anchor changed")
        source = source.replace(field_anchor, field_anchor + resolution_field, 1)

    replacements = [
        ('@PostMapping(path = "/getTransactions")', user_guard("getTransactions", "/getTransactions", "ACCESS_TRANSACTION_LOG", "transactionQueryService.adminTransactions(requestBody)")),
        ('@PostMapping(path = "/getMerchantTransactions")', merchant_guard("getMerchantTransactions", "/getMerchantTransactions", "ACCESS_TRANSACTION_LOG", "transactionQueryService.merchantTransactions(requestBody, sessionUser)")),
        ('@PostMapping(path = "/getMerchantPayments")', merchant_guard("getMerchantPayments", "/getMerchantPayments", "ACCESS_TRANSACTION_LOG", "transactionQueryService.merchantPayments(requestBody, sessionUser)")),
        ('@PostMapping(path = "/getMerchantSms")', merchant_guard("getMerchantSms", "/getMerchantSms", "ACCESS_SMS_LOG", "transactionQueryService.merchantSms(requestBody, sessionUser)")),
        ('@PostMapping(path = "/getMerchantStatement")', user_guard("getMerchantStatement", "/getMerchantStatement", "ACCESS_TRANSACTION_LOG", "transactionQueryService.adminMerchantStatement(requestBody)")),
        ('@PostMapping(path = "/getMerchantStatementByMerchant")', merchant_guard("getMerchantStatementByMerchant", "/getMerchantStatementByMerchant", "ACCESS_TRANSACTION_LOG", "transactionQueryService.ownMerchantStatement(requestBody, sessionUser)")),
    ]
    for marker, replacement in replacements:
        source = replace_method(source, marker, replacement)

    statement_marker = "public String recordStatementTx(Statement tx, String balance_type)"
    if statement_marker in source:
        source = replace_method(
            source,
            statement_marker,
            '''public String recordStatementTx(Statement tx, String balance_type) {
        return transactionResolutionService.recordStatement(tx, balance_type);
    }''',
        )

    source, count = re.subn(
        r"Common\.updateTx\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*,\s*jdbcTemplate\s*,\s*transactionManager\s*\)",
        r"transactionResolutionService.update(\1)",
        source,
    )
    if "Common.updateTx(" in source:
        raise RuntimeError("Unmigrated Common.updateTx call remains in TransactionsLogController")
    CONTROLLER.write_text(source)


def rewrite_common_pure_helpers() -> None:
    source = COMMON.read_text()
    support_field = (
        "    private static final net.citotech.cito.legacy.LegacyCommonSupport LEGACY_SUPPORT =\n"
        "            new net.citotech.cito.legacy.LegacyCommonSupport();\n\n"
    )
    anchor = "public class Common {\n"
    if "LegacyCommonSupport LEGACY_SUPPORT" not in source:
        if anchor not in source: raise RuntimeError("Common class declaration changed")
        source = source.replace(anchor, anchor + support_field, 1)
    source = replace_method(source, "public static String jsonText(", '''public static String jsonText(JSONObject obj, String key, String defaultValue) {
        return LEGACY_SUPPORT.jsonText(obj, key, defaultValue);
    }''')
    source = replace_method(source, "public static String randomNumericString(", '''public static String randomNumericString(int count) {
        return LEGACY_SUPPORT.randomNumericString(count);
    }''')
    source = replace_method(source, "public static String randomAlphaNumericString(", '''public static String randomAlphaNumericString(int count) {
        return LEGACY_SUPPORT.randomAlphaNumericString(count);
    }''')
    source = replace_method(source, "public static String urlEncodeValue(", '''public static String urlEncodeValue(String value) {
        return LEGACY_SUPPORT.urlEncodeValue(value);
    }''')
    source = replace_method(source, "public static double round(", '''public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        return java.math.BigDecimal.valueOf(value).setScale(places, java.math.RoundingMode.HALF_UP).doubleValue();
    }''')
    COMMON.write_text(source)


def main() -> None:
    rewrite_controller()
    rewrite_common_pure_helpers()
    print("God-class extraction delegates applied.")


if __name__ == "__main__":
    main()
