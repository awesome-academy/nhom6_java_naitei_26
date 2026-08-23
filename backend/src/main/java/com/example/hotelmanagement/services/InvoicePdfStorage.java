package com.example.hotelmanagement.services;

import java.time.OffsetDateTime;

public interface InvoicePdfStorage {

    void uploadPdf(String objectKey, byte[] pdfBytes);

    PresignedUrl createDownloadUrl(String objectKey);

    String getObjectUri(String objectKey);

    record PresignedUrl(String url, OffsetDateTime expiresAt) {
    }
}
