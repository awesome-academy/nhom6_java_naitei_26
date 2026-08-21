package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.GuestDocumentCryptoProperties;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuestDocumentCryptoServiceTest {

    private static final String AES_KEY = "12345678901234567890123456789012";
    private static final String HMAC_KEY = "abcdefghijklmnopqrstuvwxyz123456";

    @Test
    void encryptStoresCiphertextAndLookupHashWithoutPlaintext() {
        GuestDocumentCryptoService service = createService();

        GuestDocumentCryptoService.EncryptedDocument encryptedDocument =
                service.encrypt(" 012345678901 ");

        assertThat(encryptedDocument.ciphertext()).isNotEmpty();
        assertThat(encryptedDocument.lookupHash()).hasSize(32);
        assertThat(new String(encryptedDocument.ciphertext())).doesNotContain("012345678901");
        assertThat(service.decrypt(encryptedDocument.ciphertext())).isEqualTo("012345678901");
    }

    @Test
    void calculateLookupHashIsStableForNormalizedInput() {
        GuestDocumentCryptoService service = createService();

        byte[] firstHash = service.calculateLookupHash(" ab123 ");
        byte[] secondHash = service.calculateLookupHash("AB123");

        assertThat(Arrays.equals(firstHash, secondHash)).isTrue();
    }

    @Test
    void encryptRejectsMissingEncryptionKey() {
        GuestDocumentCryptoService service = new GuestDocumentCryptoService(
                new GuestDocumentCryptoProperties("", HMAC_KEY)
        );

        assertThatThrownBy(() -> service.encrypt("012345678901"))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Guest document encryption key is not configured");
    }

    private GuestDocumentCryptoService createService() {
        return new GuestDocumentCryptoService(
                new GuestDocumentCryptoProperties(AES_KEY, HMAC_KEY)
        );
    }
}
