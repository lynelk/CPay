  package net.citotech.cito.identity.provider.gnugrid;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.citotech.cito.identity.domain.CheckOutcome;
import net.citotech.cito.identity.domain.ValidationCapability;
import net.citotech.cito.identity.provider.CheckContext;
import net.citotech.cito.identity.provider.ProviderCheckRequest;
import net.citotech.cito.identity.provider.ProviderCheckResult;
import net.citotech.cito.identity.provider.ValidationProviderAdapter;
import net.citotech.cito.identity.provider.ValidationProviderException;
import org.springframework.stereotype.Component;

/**
 * gnuGrid CRB provider adapter (ISO domain mapping: identity/provider/gnugrid). Converges the S5
 * pilot's NIN integration into the generalized {@link ValidationProviderAdapter} SPI and exposes
 * the documented gnuGrid API families: ID Validation (NIN / personal information), phone
 * (subscriber/ownership) validation, Enquiries (credit/KYC enquiries and report retrieval), and
 * Credit Score (CRB/MNO/SACCO/COMBINED).
 *
 * <p>Provider responses are normalized to evidence; CPay's policy engine owns the final identity
 * or credit decision. Technical failures always surface as {@link CheckOutcome#ERROR} evidence and
 * never as an identity or credit rejection.
 */
@Component
public class GnuGridCrbProviderAdapter implements ValidationProviderAdapter {

    public static final String PROVIDER_CODE = "GNUGRID_CRB";

    private final GnuGridIdValidationClient idValidationClient;
    private final GnuGridPhoneValidationClient phoneValidationClient;
    private final GnuGridEnquiriesClient enquiriesClient;
    private final GnuGridCreditScoreClient creditScoreClient;

    /** Convenience constructor for legacy NIN-only wiring; the four-arg constructor is primary. */
    public GnuGridCrbProviderAdapter(GnuGridIdValidationClient idValidationClient) {
        this(
                idValidationClient,
                null,
                null,
                null);
    }

    public GnuGridCrbProviderAdapter(
            GnuGridIdValidationClient idValidationClient,
            GnuGridPhoneValidationClient phoneValidationClient,
            GnuGridEnquiriesClient enquiriesClient,
            GnuGridCreditScoreClient creditScoreClient) {
        this.idValidationClient = idValidationClient;
        this.phoneValidationClient = phoneValidationClient;
        this.enquiriesClient = enquiriesClient;
        this.creditScoreClient = creditScoreClient;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public Set<ValidationCapability> capabilities() {
        return Set.of(
                ValidationCapability.NIN,
                ValidationCapability.PERSONAL_INFORMATION,
                ValidationCapability.PHONE_OWNERSHIP,
                ValidationCapability.KYC_REPORT,
                ValidationCapability.CREDIT_ENQUIRY,
                ValidationCapability.CREDIT_REPORT,
                ValidationCapability.CREDIT_SCORE_CRB,
                ValidationCapability.CREDIT_SCORE_MNO,
                ValidationCapability.CREDIT_SCORE_SACCO,
                ValidationCapability.CREDIT_SCORE_COMBINED);
    }

    @Override
    public boolean supports(CheckContext context) {
        if (context == null || context.capability() == null) {
            return false;
        }
        return capabilities().contains(context.capability());
    }

    @Override
    public ProviderCheckResult execute(ProviderCheckRequest request) {
        return switch (request.capability()) {
            case NIN, PERSONAL_INFORMATION -> executeIdValidation(request);
            case PHONE_OWNERSHIP -> executePhoneValidation(request);
            case KYC_REPORT, CREDIT_ENQUIRY, CREDIT_REPORT -> executeEnquiry(request);
            case CREDIT_SCORE_CRB, CREDIT_SCORE_MNO, CREDIT_SCORE_SACCO, CREDIT_SCORE_COMBINED ->
                    executeCreditScore(request);
            default ->
                    throw new UnsupportedOperationException(
                            "gnuGrid capability not implemented: " + request.capability());
        };
    }

    private ProviderCheckResult executeIdValidation(ProviderCheckRequest request) {
        String reference = "CHK-" + request.checkId();
        try {
            GnuGridIdValidationClient.ProviderIdValidationResult result =
                    idValidationClient.validate(reference, request.attributes());
            if (result.match()) {
                return new ProviderCheckResult(
                        CheckOutcome.PASS,
                        null,
                        List.of(result.normalizedCode()),
                        result.attributes(),
                        result.providerReference(),
                        List.of(),
                        null);
            }
            return new ProviderCheckResult(
                    CheckOutcome.FAIL,
                    null,
                    List.of(result.normalizedCode()),
                    Map.of(),
                    result.providerReference(),
                    List.of(),
                    null);
        } catch (ValidationProviderException ex) {
            return technicalError(ex);
        }
    }

    private ProviderCheckResult executePhoneValidation(ProviderCheckRequest request) {
        requireClient(phoneValidationClient, "phone validation");
        String reference = "CHK-" + request.checkId();
        try {
            GnuGridPhoneValidationClient.ProviderPhoneValidationResult result =
                    phoneValidationClient.validate(reference, request.attributes());
            if (result.matched()) {
                return new ProviderCheckResult(
                        CheckOutcome.PASS,
                        null,
                        List.of(result.normalizedCode()),
                        result.attributes(),
                        result.providerReference(),
                        List.of(),
                        null);
            }
            return new ProviderCheckResult(
                    CheckOutcome.FAIL,
                    null,
                    List.of(result.normalizedCode()),
                    Map.of(),
                    result.providerReference(),
                    List.of(),
                    null);
        } catch (ValidationProviderException ex) {
            return technicalError(ex);
        }
    }

    private ProviderCheckResult executeEnquiry(ProviderCheckRequest request) {
        requireClient(enquiriesClient, "enquiries");
        String reference = "CHK-" + request.checkId();
        try {
            String operation = request.attributes().getOrDefault("operation", "SUBMIT");
            GnuGridEnquiriesClient.ProviderEnquiryResult result =
                    enquiriesClient.execute(reference, operation, request.attributes());
            switch (result.status()) {
                case "PENDING" -> {
                    return new ProviderCheckResult(
                            CheckOutcome.PENDING,
                            null,
                            List.of(result.normalizedCode()),
                            result.attributes(),
                            result.providerReference(),
                            List.of(),
                            null);
                }
                case "COMPLETED" -> {
                    if ("CREDIT_REPORT_NOT_FOUND".equals(result.normalizedCode())) {
                        return new ProviderCheckResult(
                                CheckOutcome.FAIL,
                                null,
                                List.of(result.normalizedCode()),
                                Map.of(),
                                result.providerReference(),
                                List.of(),
                                null);
                    }
                    return new ProviderCheckResult(
                            CheckOutcome.PASS,
                            null,
                            List.of(result.normalizedCode()),
                            result.attributes(),
                            result.providerReference(),
                            result.protectedArtifactReference() == null
                                    ? List.of()
                                    : List.of(
                                            new ProviderCheckResult.EvidenceReference(
                                                    "CREDIT_REPORT",
                                                    result.protectedArtifactReference())),
                            null);
                }
                default ->
                        throw new ValidationProviderException(
                                PROVIDER_CODE,
                                "PROVIDER_INCONCLUSIVE",
                                "gnuGrid enquiry returned an unexpected status");
            }
        } catch (ValidationProviderException ex) {
            return technicalError(ex);
        }
    }

    private ProviderCheckResult executeCreditScore(ProviderCheckRequest request) {
        requireClient(creditScoreClient, "credit score");
        String reference = "CHK-" + request.checkId();
        String scoreType =
                request.capability()
                        .name()
                        .replace("CREDIT_SCORE_", "");
        Map<String, String> attributes =
                new java.util.LinkedHashMap<>(request.attributes());
        attributes.put("scoreType", scoreType);
        try {
            GnuGridCreditScoreClient.ProviderScoreResult result =
                    creditScoreClient.score(reference, attributes);
            CheckOutcome outcome =
                    "SCORE_AVAILABLE".equals(result.status())
                            ? CheckOutcome.PASS
                            : CheckOutcome.FAIL;
            return new ProviderCheckResult(
                    outcome,
                    null,
                    List.of(
                            "SCORE_AVAILABLE".equals(result.status())
                                    ? "CREDIT_SCORE_AVAILABLE"
                                    : "CREDIT_SCORE_NOT_FOUND"),
                    result.attributes() == null ? Map.of() : result.attributes(),
                    result.providerReference(),
                    List.of(),
                    null);
        } catch (ValidationProviderException ex) {
            return technicalError(ex);
        }
    }

    private void requireClient(Object client, String feature) {
        if (client == null) {
            throw new ValidationProviderException(
                    PROVIDER_CODE,
                    "PROVIDER_CONFIGURATION",
                    "gnuGrid " + feature + " client is not configured");
        }
    }

    private ProviderCheckResult technicalError(ValidationProviderException ex) {
        return new ProviderCheckResult(
                CheckOutcome.ERROR,
                null,
                List.of(ex.normalizedCode()),
                Map.of(),
                null,
                List.of(),
                null);
    }
}
