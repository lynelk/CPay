package net.citotech.cito.gateway;

import net.citotech.cito.ErrorCatalog;
import net.citotech.cito.ErrorCatalog.Category;
import net.citotech.cito.ErrorCatalog.Entry;

/**
 * Translates a raw provider HTTP response - or a caught internal exception around a provider call -
 * into a merchant-safe, stable-coded result. This is the audit C6/J7 fix.
 *
 * <p>Provider adapter code routinely did {@code gatewayResponse.setMessage(response.getResponse())}
 * (see {@code AirtelMoneyOpenApiPaymentGateway.submit()}, {@code ProviderEndpointExecutionService},
 * {@code ProviderEndpointClient} before this change) - putting the literal, unfiltered provider
 * response body directly into a field that reaches merchant-facing responses on both API generations:
 * v1 via {@code GeneralException}/{@code GeneralSuccessResponse#getApiTxMessage} (nested
 * {@code txDetails.message}), and v2 via {@code AdapterNativePaymentService} /
 * {@code NativePaymentsV2Controller} ({@code PaymentResult.message} and {@code .providerResponse}).
 * That is a real, currently-reachable leak, not a theoretical one - traced end to end while building
 * this class; see the wired call sites and their tests for the exact path.
 *
 * <p>Separately (audit J7), some of those same catch blocks handed the raw internal exception message
 * (a {@code JSONException}, {@code IOException}, encryption failure, ...) straight to the merchant
 * field too, with no log entry at all - the cause was neither safely surfaced nor actually logged.
 * {@link #translateInternalFailure(Throwable)} covers that case: a short, stable, non-sensitive reason
 * code for the response, while the caller is expected to log the full exception for internal
 * diagnosis and keep the raw provider/exception detail in {@code GateWayResponse#requestTrace} - a
 * field that already exists specifically for this (it is stored internally as
 * {@code tx_request_trace}/{@code tx_update_trace} and is never serialized into a merchant-facing
 * response - see {@code GeneralException/GeneralSuccessResponse#getApiTxMessage} and
 * {@code PaymentResult}, neither of which include it).
 *
 * <p>Reuses {@link ErrorCatalog}'s {@code Entry}/{@code Category} shape rather than inventing a
 * parallel taxonomy - {@code stableCode}/{@code category}/{@code retryable} mean exactly what they
 * mean for the legacy numeric codes registered in {@link ErrorCatalog}, and {@code PROVIDER_DECLINED}/
 * {@code PROVIDER_TIMEOUT} are the same stable codes already documented in {@code Docs/Error-catalog.md}.
 *
 * <h2>How to adopt this elsewhere</h2>
 * Any adapter/gateway class that currently does {@code gwResponse.setMessage(rawProviderText)} (or
 * {@code gwResponse.setMessage(exception.getMessage())} in a catch block) should instead:
 * <ol>
 *   <li>Call {@link #translateProviderResponse(int, String, String)} (HTTP-style provider failures) or
 *       {@link #translateInternalFailure(Throwable)} (a caught internal exception), and use
 *       {@code translation.merchantMessage()} for the merchant-facing message field.</li>
 *   <li>Keep the raw text only in {@code GateWayResponse#requestTrace} (or the logger) - never in
 *       {@code message}.</li>
 * </ol>
 * See {@code AirtelMoneyOpenApiPaymentGateway.submit()}/{@code applySuccessResponse()},
 * {@code ProviderEndpointExecutionService#execute}, and {@code ProviderEndpointClient#execute} for
 * worked examples. {@code AirtelMoneyPaymentGateway} (classic Airtel XML gateway),
 * {@code MTNMoMoPaymentGateway}, and {@code SafariComPaymentGateway} have the identical
 * raw-passthrough pattern (`gwResponse.setMessage("Response Data: "+rs.getResponse())` and similar) and
 * are good candidates for the same mechanical change - deliberately left for a follow-up rather than
 * rewriting every adapter in this one pass.
 */
public final class ProviderErrorTranslator {

    private ProviderErrorTranslator() {
    }

    /** A merchant-safe translation: the stable catalog entry plus the exact message to surface. */
    public record Translation(Entry entry, String merchantMessage) {
        public String stableCode() {
            return entry.stableCode();
        }

        public Category category() {
            return entry.category();
        }

        public boolean retryable() {
            return entry.retryable();
        }
    }

    private static final Translation PROVIDER_DECLINED = new Translation(
            new Entry("PROVIDER_DECLINED", Category.PROVIDER, false),
            "The payment provider declined this request.");

    private static final Translation PROVIDER_UNAVAILABLE = new Translation(
            new Entry("PROVIDER_TIMEOUT", Category.PROVIDER, true),
            "The payment provider is temporarily unavailable, retry is safe.");

    private static final Translation AUTH_INVALID = new Translation(
            new Entry("AUTH_CREDENTIALS_INVALID", Category.AUTHENTICATION, false),
            "CPay could not authenticate with the payment provider. This has been flagged for the operations team.");

    /**
     * Audit C6/J7 follow-up: a response that failed our own signature verification (see
     * {@code YoPaymentsCallbackVerifier}/{@code PaymentChannelAdapter#verifyCallback}) is NOT a
     * business decline - the provider may never have sent this response at all. Deliberately a
     * distinct, non-retryable AUTHENTICATION-category entry rather than falling through to
     * {@link #translateProviderResponse}'s generic 2xx-body handling, which would otherwise collapse
     * this into indistinguishable-from-"provider declined" and lose a security-relevant signal that
     * the response could not be trusted.
     */
    public static final Translation SIGNATURE_VERIFICATION_FAILED = new Translation(
            new Entry("PROVIDER_RESPONSE_UNTRUSTED", Category.AUTHENTICATION, false),
            "CPay could not verify this payment provider's response as authentic. It has been rejected and flagged for review.");

    // Never returns null (see ErrorCatalog#lookup) - deliberately reusing the catalog's existing
    // generic fallback entry (SYSTEM_UNAVAILABLE, retryable) rather than duplicating it here. The key
    // is never a registered legacy numeric code, so this always hits the fallback branch.
    private static final Translation UNCLASSIFIED = new Translation(
            ErrorCatalog.lookup("PROVIDER_RESPONSE_UNCLASSIFIED"),
            "CPay could not confirm the outcome of this request with the payment provider. Check the transaction status before retrying.");

    /**
     * Translates a raw provider HTTP response into a merchant-safe result.
     *
     * @param httpStatus   the provider's HTTP status code, or {@code <= 0} when there was no response
     *                      at all (connection failure, timeout before any status line).
     * @param rawBody      the raw provider response body - used only to decide whether any signal is
     *                      present at all; its content is never echoed back to the caller.
     * @param providerCode a provider-specific result/error code already parsed out by the caller when
     *                      available (e.g. Airtel OpenAPI's {@code status.result_code}, M-Pesa's
     *                      {@code ResultCode}), or {@code null}/blank when none was parsed.
     */
    public static Translation translateProviderResponse(int httpStatus, String rawBody, String providerCode) {
        if (httpStatus <= 0) {
            return PROVIDER_UNAVAILABLE;
        }
        if (httpStatus == 401 || httpStatus == 403) {
            return AUTH_INVALID;
        }
        if (httpStatus == 408 || httpStatus == 429 || httpStatus >= 500) {
            return PROVIDER_UNAVAILABLE;
        }
        if (httpStatus >= 400) {
            return PROVIDER_DECLINED;
        }
        // httpStatus is 2xx/3xx: the caller only reaches this branch once it has independently
        // classified the transaction as failed via its own provider-specific business-decline signal
        // (e.g. Airtel's status.result_code / transaction.status, M-Pesa's ResultCode != 0) - an HTTP
        // "success" is not itself proof of a successful payment.
        if (isBlank(rawBody) && isBlank(providerCode)) {
            // No usable signal at all (empty/unparseable body, no provider code) - fall back to the
            // catalog's generic, retryable "we don't know" entry rather than guessing.
            return UNCLASSIFIED;
        }
        return PROVIDER_DECLINED;
    }

    /** Convenience overload when no provider-specific result code was parsed. */
    public static Translation translateProviderResponse(int httpStatus, String rawBody) {
        return translateProviderResponse(httpStatus, rawBody, null);
    }

    /**
     * Audit J7: for an internal exception (JSON parsing, IO, crypto, ...) caught around a provider
     * call - not a provider HTTP error - returns a stable, non-sensitive reason so the failure is not
     * collapsed into a totally generic code with the cause visible only in the logger (or, worse, with
     * the raw exception message handed to the merchant instead). Never includes
     * {@code cause.getMessage()} or any stack trace detail in the returned message - callers are
     * expected to log {@code cause} themselves for internal diagnosis.
     */
    public static Translation translateInternalFailure(Throwable cause) {
        if (cause instanceof java.io.IOException) {
            // Reuse Category.PROVIDER: an IOException around a provider call almost always means CPay
            // could not reach the provider (connect/read failure), not an internal CPay defect.
            return new Translation(new Entry("PROVIDER_CONNECTION_ERROR", Category.PROVIDER, true),
                    "CPay could not reach the payment provider. Retry is safe.");
        }
        return new Translation(new Entry(internalReasonCode(cause), Category.SYSTEM, true),
                "CPay could not complete this request due to an internal error. Retry is safe; "
                        + "contact support with the request id if this persists.");
    }

    /**
     * Short, stable, non-sensitive classification of an internal exception - never the raw
     * message/stack trace. Exposed separately so callers that just want a reason code for their own
     * logging/response shape (rather than a full {@link Translation}) can use it directly.
     */
    public static String internalReasonCode(Throwable cause) {
        if (cause == null) {
            return "SYSTEM_UNKNOWN_ERROR";
        }
        if (cause instanceof org.json.JSONException) {
            return "SYSTEM_JSON_PARSE_ERROR";
        }
        if (cause instanceof java.io.IOException) {
            return "SYSTEM_PROVIDER_IO_ERROR";
        }
        if (cause instanceof java.security.GeneralSecurityException) {
            return "SYSTEM_CRYPTO_ERROR";
        }
        if (cause instanceof NumberFormatException) {
            return "SYSTEM_MALFORMED_PROVIDER_VALUE";
        }
        return "SYSTEM_UNKNOWN_ERROR";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
