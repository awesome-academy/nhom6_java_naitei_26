package com.example.hotelmanagement.services;

import com.example.hotelmanagement.exceptions.PaymentGatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class SePaySignatureService {

    private static final Logger log = LoggerFactory.getLogger(SePaySignatureService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final List<String> SIGNED_FIELDS = List.of(
            "order_amount",
            "merchant",
            "currency",
            "operation",
            "order_description",
            "order_invoice_number",
            "customer_id",
            "payment_method",
            "success_url",
            "error_url",
            "cancel_url"
    );

    public String signCheckoutFields(Map<String, String> fields, String secretKey) {
        String signedData = SIGNED_FIELDS.stream()
                .filter(fields::containsKey)
                .map(field -> field + "=" + fields.get(field))
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return Base64.getEncoder().encodeToString(sign(signedData, secretKey));
    }

    public boolean matchesSecretKey(String expectedSecretKey, String providedSecretKey) {
        if (expectedSecretKey == null || providedSecretKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedSecretKey.getBytes(StandardCharsets.UTF_8),
                providedSecretKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private byte[] sign(String data, String secretKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            log.error("Unable to calculate SePay checkout signature", exception);
            throw new PaymentGatewayException("Unable to secure the SePay payment request", exception);
        }
    }
}
