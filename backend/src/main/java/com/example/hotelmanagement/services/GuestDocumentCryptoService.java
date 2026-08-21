package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.GuestDocumentCryptoProperties;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.GuestDocumentCryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Service
public class GuestDocumentCryptoService {

    private static final Logger log = LoggerFactory.getLogger(GuestDocumentCryptoService.class);
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int AES_256_KEY_BYTES = 32;
    private static final int MIN_HMAC_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final GuestDocumentCryptoProperties properties;
    private final SecureRandom secureRandom;

    @Autowired
    public GuestDocumentCryptoService(GuestDocumentCryptoProperties properties) {
        this(properties, new SecureRandom());
    }

    GuestDocumentCryptoService(
            GuestDocumentCryptoProperties properties,
            SecureRandom secureRandom
    ) {
        this.properties = properties;
        this.secureRandom = secureRandom;
    }

    public EncryptedDocument encrypt(String documentNumber) {
        String normalizedDocumentNumber = normalizeDocumentNumber(documentNumber);
        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(resolveAesKey(), "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );
            byte[] ciphertext = cipher.doFinal(normalizedDocumentNumber.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return new EncryptedDocument(payload, calculateLookupHash(normalizedDocumentNumber));
        } catch (GeneralSecurityException exception) {
            log.error("Failed to encrypt guest identity document", exception);
            throw new GuestDocumentCryptoException("Failed to encrypt guest identity document", exception);
        }
    }

    public String decrypt(byte[] encryptedDocumentNumber) {
        if (encryptedDocumentNumber == null || encryptedDocumentNumber.length <= GCM_IV_BYTES) {
            throw new BusinessValidationException("Guest identity document is not available");
        }

        ByteBuffer payload = ByteBuffer.wrap(encryptedDocumentNumber);
        byte[] iv = new byte[GCM_IV_BYTES];
        payload.get(iv);
        byte[] ciphertext = new byte[payload.remaining()];
        payload.get(ciphertext);

        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(resolveAesKey(), "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            log.error("Failed to decrypt guest identity document", exception);
            throw new GuestDocumentCryptoException("Failed to decrypt guest identity document", exception);
        }
    }

    public byte[] calculateLookupHash(String documentNumber) {
        String normalizedDocumentNumber = normalizeDocumentNumber(documentNumber);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(resolveHmacKey(), HMAC_ALGORITHM));
            return mac.doFinal(normalizedDocumentNumber.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            log.error("Failed to hash guest identity document", exception);
            throw new GuestDocumentCryptoException("Failed to hash guest identity document", exception);
        }
    }

    private byte[] resolveAesKey() {
        return resolveSecret(
                properties.encryptionKey(),
                AES_256_KEY_BYTES,
                AES_256_KEY_BYTES,
                "Guest document encryption key"
        );
    }

    private byte[] resolveHmacKey() {
        return resolveSecret(
                properties.lookupHmacKey(),
                MIN_HMAC_KEY_BYTES,
                Integer.MAX_VALUE,
                "Guest document lookup HMAC key"
        );
    }

    private byte[] resolveSecret(
            String configuredValue,
            int minBytes,
            int maxBytes,
            String fieldName
    ) {
        if (configuredValue == null || configuredValue.isBlank()) {
            throw new BusinessValidationException(fieldName + " is not configured");
        }
        byte[] decoded = decodeBase64(configuredValue.strip());
        if (decoded != null && decoded.length >= minBytes && decoded.length <= maxBytes) {
            return decoded;
        }

        byte[] raw = configuredValue.getBytes(StandardCharsets.UTF_8);
        if (raw.length >= minBytes && raw.length <= maxBytes) {
            return raw;
        }
        if (minBytes == maxBytes) {
            throw new BusinessValidationException(fieldName + " must be exactly " + minBytes + " bytes");
        }
        throw new BusinessValidationException(fieldName + " must be at least " + minBytes + " bytes");
    }

    private byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalizeDocumentNumber(String documentNumber) {
        if (documentNumber == null || documentNumber.isBlank()) {
            throw new BusinessValidationException("Guest identity document number cannot be blank");
        }
        return documentNumber.strip().toUpperCase(Locale.ROOT);
    }

    public record EncryptedDocument(
            byte[] ciphertext,
            byte[] lookupHash
    ) {
    }
}
