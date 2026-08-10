#!/usr/bin/env python3
"""Deterministic, idempotent source rewrite for the legacy god-class extraction track."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "InitializrSpringbootProjectFresh" / "src" / "main" / "java" / "net" / "citotech" / "cito"
CONTROLLER = BACKEND / "TransactionsLogController.java"
COMMON = BACKEND / "Common.java"
MONEY_ENGINE = BACKEND / "LegacyMoneyMovementEngine.java"
STATEMENT_ENGINE = BACKEND / "LegacyStatementEngine.java"
RESOLUTION_ENGINE = BACKEND / "TransactionResolutionEngine.java"


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


def method_text(source: str, marker: str) -> str:
    start, end = method_span(source, marker)
    return source[start:end]


def replace_method(source: str, marker: str, replacement: str) -> str:
    start, end = method_span(source, marker)
    return source[:start] + replacement.rstrip() + source[end:]


def remove_method(source: str, marker: str) -> str:
    start, end = method_span(source, marker)
    while end < len(source) and source[end] in " \t": end += 1
    if end < len(source) and source[end] == "\n": end += 1
    return source[:start] + source[end:]


def imports_of(source: str) -> str:
    return "\n".join(line for line in source.splitlines() if line.startswith("import "))


def engine_source(name: str, imports: str, methods: list[str], javadoc: str) -> str:
    body = "\n\n".join(method.strip() for method in methods)
    body = re.sub(r"(?<![\w.])recordStatementTx\(", "Common.recordStatementTx(", body)
    body = re.sub(r"(?<![\w.])enqueueMerchantCallback\(", "Common.enqueueMerchantCallback(", body)
    return (
        "package net.citotech.cito;\n\n"
        + imports
        + "\n\n/** " + javadoc + " */\n"
        + "public final class " + name + " {\n"
        + "    private " + name + "() {}\n\n"
        + body
        + "\n}\n"
    )


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
    source, migrated = re.subn(
        r"Common\.updateTx\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*,\s*jdbcTemplate\s*,\s*transactionManager\s*\)",
        r"transactionResolutionService.update(\1)", source)
    print(f"Migrated {migrated} direct Common.updateTx controller call(s).")

    helper = "private List<Beneficiary> getBatchBeneficiaries(long batch_id)"
    if helper in source and source.count("getBatchBeneficiaries(") == 1:
        source = remove_method(source, helper)
    CONTROLLER.write_text(source)


def rewrite_common_pure_helpers(source: str) -> str:
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
    return source


def extract_statement_engine(source: str, imports: str) -> str:
    delegate_token = "LegacyStatementEngine.recordStatementTx"
    if delegate_token in source:
        return source
    markers = [
        "public static String recordStatementTx(\n",
        "public static String recordStatementTxWithoutTransaction(\n",
        "private static String recordStatementTxCore(\n",
        "private static GatewayBalanceType resolveStatementBalanceType(\n",
        "private static void refreshMerchantChannelBalanceReadModel(\n",
        "private static BigDecimal decimal(Object value)",
    ]
    methods = [method_text(source, marker) for marker in markers]
    STATEMENT_ENGINE.write_text(engine_source(
        "LegacyStatementEngine", imports, methods,
        "Legacy statement/balance mutation engine extracted from Common; public Common methods remain compatibility delegates."))
    source = replace_method(source, markers[0], '''public static String recordStatementTx(
            Statement tx,
            String balance_type,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return LegacyStatementEngine.recordStatementTx(tx, balance_type, jdbcTemplate, transactionManager);
    }''')
    source = replace_method(source, markers[1], '''public static String recordStatementTxWithoutTransaction(
            Statement tx,
            String balance_type,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            TransactionStatus status) {
        return LegacyStatementEngine.recordStatementTxWithoutTransaction(
                tx, balance_type, jdbcTemplate, transactionManager, status);
    }''')
    for marker in markers[2:]:
        source = remove_method(source, marker)
    return source


def extract_money_engine(source: str, imports: str) -> str:
    if "LegacyMoneyMovementEngine.doPayIn" in source:
        return source
    four_in = '''public static String doPayIn(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager)'''
    five_in = '''public static String doPayIn(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            boolean skipRiskCheck)'''
    four_out = '''public static String doPayOut(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager)'''
    five_out = '''public static String doPayOut(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            boolean skipRiskCheck)'''
    helpers = [
        "private static String buildIdempotentReplayResponse(Transaction existingTx)",
        "private static String authorizeLegacyRisk(\n",
    ]
    markers = helpers + [four_in, five_in, four_out, five_out]
    methods = [method_text(source, marker) for marker in markers]
    MONEY_ENGINE.write_text(engine_source(
        "LegacyMoneyMovementEngine", imports, methods,
        "Legacy pay-in/pay-out execution engine extracted from Common while preserving the v1 compatibility signatures."))
    source = replace_method(source, four_in, '''public static String doPayIn(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return LegacyMoneyMovementEngine.doPayIn(newTx, merchant, jdbcTemplate, transactionManager);
    }''')
    source = replace_method(source, five_in, '''public static String doPayIn(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            boolean skipRiskCheck) {
        return LegacyMoneyMovementEngine.doPayIn(
                newTx, merchant, jdbcTemplate, transactionManager, skipRiskCheck);
    }''')
    source = replace_method(source, four_out, '''public static String doPayOut(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return LegacyMoneyMovementEngine.doPayOut(newTx, merchant, jdbcTemplate, transactionManager);
    }''')
    source = replace_method(source, five_out, '''public static String doPayOut(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            boolean skipRiskCheck) {
        return LegacyMoneyMovementEngine.doPayOut(
                newTx, merchant, jdbcTemplate, transactionManager, skipRiskCheck);
    }''')
    for marker in helpers:
        source = remove_method(source, marker)
    return source


def extract_resolution_engine(source: str, imports: str) -> str:
    if "TransactionResolutionEngine.update" in source:
        return source
    update_marker = "public static String updateTx(\n"
    helper_marker = "private static boolean providerReferenceAlreadyApplied(\n"
    methods = [method_text(source, helper_marker), method_text(source, update_marker)]
    RESOLUTION_ENGINE.write_text(engine_source(
        "TransactionResolutionEngine", imports, methods,
        "Transaction status-resolution and settlement/reversal command engine extracted from Common."))
    source = replace_method(source, update_marker, '''public static String updateTx(
            Transaction tx,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return TransactionResolutionEngine.updateTx(tx, jdbcTemplate, transactionManager);
    }''')
    source = remove_method(source, helper_marker)
    return source


def rewrite_common() -> None:
    source = COMMON.read_text()
    imports = imports_of(source)
    source = rewrite_common_pure_helpers(source)
    source = extract_statement_engine(source, imports)
    source = extract_money_engine(source, imports)
    source = extract_resolution_engine(source, imports)
    COMMON.write_text(source)


def main() -> None:
    rewrite_controller()
    rewrite_common()
    print("God-class extraction delegates and physical engines applied.")


if __name__ == "__main__":
    main()
