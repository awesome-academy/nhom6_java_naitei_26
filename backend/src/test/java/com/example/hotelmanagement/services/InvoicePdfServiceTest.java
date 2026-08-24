package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.invoice.InvoicePdfResponse;
import com.example.hotelmanagement.entity.Invoice;
import com.example.hotelmanagement.entity.InvoiceItem;
import com.example.hotelmanagement.entity.enums.InvoiceLineType;
import com.example.hotelmanagement.entity.enums.InvoiceStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import com.example.hotelmanagement.repositories.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoicePdfServiceTest {

    private static final String INVOICE_PUBLIC_ID = "22222222-2222-2222-2222-222222222222";
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-23T09:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoicePdfStorage invoicePdfStorage;
    @Mock
    private HotelSettingsRepository hotelSettingsRepository;

    private InvoicePdfService invoicePdfService;

    @BeforeEach
    void setUp() {
        invoicePdfService = new InvoicePdfService(
                invoiceRepository,
                invoicePdfStorage,
                hotelSettingsRepository,
                FIXED_CLOCK
        );
    }

    @Test
    void generatesAndUploadsPdfWhenNotCachedYet() {
        Invoice invoice = issuedInvoice();
        when(invoiceRepository.findByPublicId(INVOICE_PUBLIC_ID)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.saveAndFlush(invoice)).thenReturn(invoice);
        when(invoicePdfStorage.getObjectUri(anyString())).thenReturn("minio://invoices/key.pdf");
        when(invoicePdfStorage.createDownloadUrl(anyString())).thenReturn(
                new InvoicePdfStorage.PresignedUrl("https://minio/presigned", OffsetDateTime.now(FIXED_CLOCK).plusHours(1))
        );

        InvoicePdfResponse response = invoicePdfService.getDownloadUrl(INVOICE_PUBLIC_ID);

        assertThat(response.url()).isEqualTo("https://minio/presigned");
        ArgumentCaptor<byte[]> pdfCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(invoicePdfStorage).uploadPdf(anyString(), pdfCaptor.capture());
        assertThat(pdfCaptor.getValue()).isNotEmpty();
        // A real PDF file starts with the "%PDF-" magic bytes.
        assertThat(new String(pdfCaptor.getValue(), 0, 5)).isEqualTo("%PDF-");

        assertThat(invoice.getPdfStorageKey()).isNotBlank();
        assertThat(invoice.getPdfUrl()).isEqualTo("minio://invoices/key.pdf");
        verify(invoiceRepository).saveAndFlush(invoice);
    }

    @Test
    void reusesCachedPdfWithoutRegenerating() {
        Invoice invoice = issuedInvoice();
        invoice.setPdfStorageKey("invoices/20/existing.pdf");
        invoice.setPdfUrl("minio://invoices/invoices/20/existing.pdf");
        when(invoiceRepository.findByPublicId(INVOICE_PUBLIC_ID)).thenReturn(Optional.of(invoice));
        when(invoicePdfStorage.createDownloadUrl("invoices/20/existing.pdf")).thenReturn(
                new InvoicePdfStorage.PresignedUrl("https://minio/cached", OffsetDateTime.now(FIXED_CLOCK).plusHours(1))
        );

        InvoicePdfResponse response = invoicePdfService.getDownloadUrl(INVOICE_PUBLIC_ID);

        assertThat(response.url()).isEqualTo("https://minio/cached");
        verify(invoicePdfStorage, never()).uploadPdf(any(), any());
        verify(invoiceRepository, never()).saveAndFlush(any());
        verify(invoicePdfStorage).createDownloadUrl(eq("invoices/20/existing.pdf"));
    }

    @Test
    void rejectsDraftInvoice() {
        Invoice invoice = issuedInvoice();
        invoice.setStatus(InvoiceStatus.DRAFT);
        when(invoiceRepository.findByPublicId(INVOICE_PUBLIC_ID)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoicePdfService.getDownloadUrl(INVOICE_PUBLIC_ID))
                .isInstanceOf(BusinessValidationException.class);
        verify(invoicePdfStorage, never()).uploadPdf(any(), any());
    }

    @Test
    void throwsWhenInvoiceNotFound() {
        when(invoiceRepository.findByPublicId(INVOICE_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoicePdfService.getDownloadUrl(INVOICE_PUBLIC_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rendersVoidedInvoiceWithoutError() {
        Invoice invoice = issuedInvoice();
        invoice.setStatus(InvoiceStatus.VOID);
        invoice.setVoidedAt(OffsetDateTime.now(FIXED_CLOCK));
        invoice.setVoidReason("Wrong buyer info");
        when(invoiceRepository.findByPublicId(INVOICE_PUBLIC_ID)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.saveAndFlush(invoice)).thenReturn(invoice);
        when(invoicePdfStorage.getObjectUri(anyString())).thenReturn("minio://invoices/key.pdf");
        when(invoicePdfStorage.createDownloadUrl(anyString())).thenReturn(
                new InvoicePdfStorage.PresignedUrl("https://minio/presigned", OffsetDateTime.now(FIXED_CLOCK).plusHours(1))
        );

        InvoicePdfResponse response = invoicePdfService.getDownloadUrl(INVOICE_PUBLIC_ID);

        assertThat(response.url()).isEqualTo("https://minio/presigned");
        verify(invoicePdfStorage).uploadPdf(anyString(), any());
    }

    private Invoice issuedInvoice() {
        Invoice invoice = Invoice.builder()
                .publicId(INVOICE_PUBLIC_ID)
                .invoiceNumber("INV-2026-000001")
                .status(InvoiceStatus.ISSUED)
                .issuedAt(OffsetDateTime.now(FIXED_CLOCK).minusHours(1))
                .issuedBy(5L)
                .buyerName("Nguyen Van A")
                .buyerAddress("123 Le Loi, Da Nang")
                .buyerEmail("guest@example.com")
                .subtotal(money("100.00"))
                .discountTotal(money("0.00"))
                .taxTotal(money("10.00"))
                .totalAmount(money("110.00"))
                .currency("VND")
                .build();
        invoice.setId(20L);
        InvoiceItem item = InvoiceItem.builder()
                .invoice(invoice)
                .lineType(InvoiceLineType.ROOM)
                .description("Deluxe room - 2026-08-22")
                .quantity(money("1.00"))
                .unitPrice(money("100.00"))
                .lineSubtotal(money("100.00"))
                .discountAmount(money("0.00"))
                .taxPercent(money("10.00"))
                .taxAmount(money("10.00"))
                .lineTotal(money("110.00"))
                .sortOrder(10)
                .build();
        item.setId(50L);
        invoice.getItems().add(item);
        return invoice;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
