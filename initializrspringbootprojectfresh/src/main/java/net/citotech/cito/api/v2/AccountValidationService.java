package net.citotech.cito.api.v2;

import net.citotech.cito.Common;
import net.citotech.cito.DoPayGateway;
import net.citotech.cito.Model.AccountInfo;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.AccountValidationRequest;
import net.citotech.cito.api.v2.dto.AccountValidationResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AccountValidationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MerchantReadAuditService auditService;

    public AccountValidationService(NamedParameterJdbcTemplate jdbcTemplate,
                                    MerchantReadAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    public AccountValidationResponse validate(AccountValidationRequest request, Merchant merchant) {
        validateRequest(request, merchant);
        AccountInfo info = DoPayGateway.getAccountInfo(request.getMsisdn(), jdbcTemplate);
        if (info == null) {
            throw new PaymentGatewayException("Account information was not available");
        }
        auditService.record(merchant, "ACCOUNT_VALIDATION_READ", maskAccount(request.getMsisdn()));

        AccountValidationResponse response = new AccountValidationResponse();
        response.setMsisdn(request.getMsisdn());
        response.setFirstName(value(info.getFirstName()));
        response.setLastName(value(info.getLastName()));
        response.setName(value(info.getProvided_name()));
        response.setStatus(value(info.getStatus()));
        return response;
    }

    private void validateRequest(AccountValidationRequest request, Merchant merchant) {
        if (request == null) {
            throw new PaymentGatewayException("Request body is required");
        }
        if (isBlank(request.getMerchantNumber())) {
            throw new PaymentGatewayException("merchantNumber is required");
        }
        if (isBlank(request.getMsisdn())) {
            throw new PaymentGatewayException("msisdn is required");
        }
        validateMerchant(merchant, request.getMerchantNumber(), Common.API_ACCOUNT_VALIDATION);
    }

    private void validateMerchant(Merchant merchant, String merchantNumber, String requiredApi) {
        if (merchant == null || !merchantNumber.equals(merchant.getAccount_number())) {
            throw new PaymentGatewayException("Verified merchant does not match request merchant");
        }
        if (!"ACTIVE".equalsIgnoreCase(merchant.getStatus())) {
            throw new PaymentGatewayException("Merchant is not active");
        }
        if (!hasApi(merchant, requiredApi)) {
            throw new PaymentGatewayException("Merchant is not allowed to access " + requiredApi);
        }
    }

    private boolean hasApi(Merchant merchant, String requiredApi) {
        String[] allowedApis = merchant.getAllowed_apis();
        if (allowedApis == null) {
            return false;
        }
        for (String api : allowedApis) {
            if (requiredApi.equals(api)) {
                return true;
            }
        }
        return false;
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 6) {
            return "masked";
        }
        return account.substring(0, 3) + "****" + account.substring(account.length() - 3);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
