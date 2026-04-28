package net.citotech.cito.security;

import net.citotech.cito.Common;
import net.citotech.cito.GeneralException;
import net.citotech.cito.Model.Merchant;

import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for verifying RSA SHA256 signatures from merchant API requests.
 */
public class SignatureVerificationService {

    private static final Logger logger = Logger.getLogger(SignatureVerificationService.class.getName());

    /**
     * Verifies the RSA SHA256 signature for a merchant API request.
     *
     * @param merchant        the merchant whose public key is used for verification
     * @param signedData      the data that was signed (concatenation of request fields)
     * @param signatureBase64 the base64-encoded signature from the request
     * @return null if signature is valid, or an error JSON string if invalid
     */
    public static String verify(Merchant merchant, String signedData, String signatureBase64) {
        if (merchant.getPublic_key() == null || merchant.getPublic_key().isEmpty()) {
            return GeneralException.getError("115", GeneralException.ERRORS_115);
        }

        try {
            Signature sign = Signature.getInstance("SHA256withRSA");
            String base64_public_key = merchant.getPublic_key();
            base64_public_key = base64_public_key.replace("-----BEGIN PUBLIC KEY-----\n", "");
            String base64_cleaned = base64_public_key.replace("\n-----END PUBLIC KEY-----\n", "");
            PublicKey publicKey = Common.getPublicKeyFromBase64String(base64_cleaned);
            if (publicKey == null) {
                return GeneralException.getError("115", GeneralException.ERRORS_115);
            }
            sign.initVerify(publicKey);
            sign.update(signedData.getBytes());

            byte[] signature_content;
            try {
                signature_content = Base64.getDecoder().decode(signatureBase64);
            } catch (Exception e) {
                return GeneralException.getError("122", GeneralException.ERRORS_122);
            }

            if (signature_content.length < 256) {
                return GeneralException.getError("122", GeneralException.ERRORS_122);
            }

            if (!sign.verify(signature_content)) {
                return GeneralException.getError("116", GeneralException.ERRORS_116);
            }

            return null; // signature is valid

        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Signature verification error: " + ex.getMessage(), ex);
            return GeneralException.getError("116", GeneralException.ERRORS_116);
        }
    }
}
