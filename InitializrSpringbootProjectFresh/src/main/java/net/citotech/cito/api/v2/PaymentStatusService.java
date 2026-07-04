package net.citotech.cito.api.v2;

import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.api.v2.dto.PaymentStatusResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.repository.TransactionRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentStatusService {
    private final TransactionRepository transactionRepository;

    public PaymentStatusService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public PaymentStatusResponse getStatus(Merchant merchant, String reference) {
        validateMerchant(merchant);
        if (reference == null || reference.trim().isEmpty()) {
            throw new PaymentGatewayException("reference is required");
        }
        Transaction transaction = transactionRepository
                .findByMerchantReference(merchant.getId(), reference.trim())
                .orElseThrow(() -> new PaymentGatewayException("Transaction reference was not found"));
        return new PaymentStatusResponse(
                transaction.getTx_merchant_ref(),
                transaction.getStatus(),
                transaction.getGateway_id(),
                transaction.getTx_gateway_ref(),
                transaction.getTx_unique_id());
    }

    private void validateMerchant(Merchant merchant) {
        if (merchant == null) {
            throw new PaymentGatewayException("Merchant was not found");
        }
        if (!"ACTIVE".equalsIgnoreCase(merchant.getStatus())) {
            throw new PaymentGatewayException("Merchant is not active");
        }
        boolean allowed = false;
        String[] allowedApis = merchant.getAllowed_apis();
        if (allowedApis != null) {
            for (String api : allowedApis) {
                if (Common.API_TRANSACTION_CHECKSTATUS.equals(api)) {
                    allowed = true;
                    break;
                }
            }
        }
        if (!allowed) {
            throw new PaymentGatewayException("Merchant is not allowed to access " + Common.API_TRANSACTION_CHECKSTATUS);
        }
    }
}

