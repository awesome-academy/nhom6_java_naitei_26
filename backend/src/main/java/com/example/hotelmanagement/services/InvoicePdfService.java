package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.invoice.InvoicePdfResponse;
import com.example.hotelmanagement.entity.Invoice;
import com.example.hotelmanagement.entity.InvoiceItem;
import com.example.hotelmanagement.entity.enums.InvoiceStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.exceptions.StorageUnavailableException;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import com.example.hotelmanagement.repositories.InvoiceRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;

/**
 * Renders invoice PDFs with OpenPDF and stores them in the MinIO "invoices" bucket.
 * Known limitation: rendering uses the standard (non-embedded) Helvetica font, which only
 * supports CP1252 — Vietnamese diacritics will not render correctly until a Unicode TTF is
 * bundled under resources/fonts and wired into {@link #resolveFont}.
 */
@Service
@Transactional
public class InvoicePdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String[] TABLE_HEADERS = {
            "Type", "Description", "Qty", "Unit Price", "Subtotal", "Discount", "Tax", "Total"
    };
    private static final float[] TABLE_WIDTHS = {8f, 30f, 7f, 12f, 12f, 10f, 9f, 12f};

    private final InvoiceRepository invoiceRepository;
    private final InvoicePdfStorage invoicePdfStorage;
    private final HotelSettingsRepository hotelSettingsRepository;
    private final Clock clock;

    public InvoicePdfService(
            InvoiceRepository invoiceRepository,
            InvoicePdfStorage invoicePdfStorage,
            HotelSettingsRepository hotelSettingsRepository,
            Clock clock
    ) {
        this.invoiceRepository = invoiceRepository;
        this.invoicePdfStorage = invoicePdfStorage;
        this.hotelSettingsRepository = hotelSettingsRepository;
        this.clock = clock;
    }

    @PreAuthorize(PermissionExpressions.INVOICE_ISSUE)
    public InvoicePdfResponse getDownloadUrl(String invoicePublicId) {
        Invoice invoice = getExistingInvoice(invoicePublicId);
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new BusinessValidationException("Only an ISSUED or VOID invoice has a PDF");
        }

        String objectKey = invoice.getPdfStorageKey();
        if (objectKey == null) {
            objectKey = buildObjectKey(invoice);
            byte[] pdfBytes = renderPdf(invoice);
            invoicePdfStorage.uploadPdf(objectKey, pdfBytes);
            invoice.setPdfStorageKey(objectKey);
            invoice.setPdfUrl(invoicePdfStorage.getObjectUri(objectKey));
            invoiceRepository.saveAndFlush(invoice);
        }

        InvoicePdfStorage.PresignedUrl downloadUrl = invoicePdfStorage.createDownloadUrl(objectKey);
        return new InvoicePdfResponse(downloadUrl.url(), downloadUrl.expiresAt());
    }

    private Invoice getExistingInvoice(String invoicePublicId) {
        if (invoicePublicId == null || invoicePublicId.isBlank()) {
            throw new BusinessValidationException("Invoice public id cannot be blank");
        }
        return invoiceRepository.findByPublicId(invoicePublicId.strip())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoicePublicId));
    }

    private String buildObjectKey(Invoice invoice) {
        return "invoices/" + invoice.getId() + "/" + invoice.getPublicId() + ".pdf";
    }

    private byte[] renderPdf(Invoice invoice) {
        Document document = new Document(PageSize.A4, 36, 36, 54, 54);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font titleFont = resolveFont(Font.BOLD, 16);
            Font sectionFont = resolveFont(Font.BOLD, 11);
            Font normalFont = resolveFont(Font.NORMAL, 10);
            Font mutedFont = resolveFont(Font.NORMAL, 9);

            addHotelHeader(document, titleFont, mutedFont);
            addInvoiceHeading(document, invoice, titleFont, sectionFont, normalFont);
            addBuyerSection(document, invoice, sectionFont, normalFont);
            document.add(buildItemsTable(invoice, normalFont));
            addTotalsSection(document, invoice, normalFont, sectionFont);
            addFooter(document, mutedFont);
        } catch (Exception exception) {
            throw new StorageUnavailableException("Could not render invoice PDF", exception);
        } finally {
            document.close();
        }
        return outputStream.toByteArray();
    }

    private void addHotelHeader(Document document, Font titleFont, Font mutedFont) throws Exception {
        document.add(new Paragraph(settingOrDefault("hotel_name", "Hotel"), titleFont));
        addLineIfPresent(document, settingOrDefault("hotel_address", null), mutedFont);
        String contactLine = joinNonBlank(
                prefixIfPresent("Tel: ", settingOrDefault("hotel_phone", null)),
                prefixIfPresent("Email: ", settingOrDefault("hotel_email", null))
        );
        addLineIfPresent(document, contactLine, mutedFont);
        addLineIfPresent(
                document,
                prefixIfPresent("Tax code: ", settingOrDefault("hotel_tax_code", null)),
                mutedFont
        );
        document.add(Chunk.NEWLINE);
    }

    private void addInvoiceHeading(
            Document document,
            Invoice invoice,
            Font titleFont,
            Font sectionFont,
            Font normalFont
    ) throws Exception {
        Paragraph heading = new Paragraph(
                invoice.getStatus() == InvoiceStatus.VOID ? "INVOICE (VOID)" : "INVOICE",
                titleFont
        );
        heading.setAlignment(Element.ALIGN_CENTER);
        document.add(heading);
        document.add(new Paragraph("Invoice No: " + invoice.getInvoiceNumber(), sectionFont));
        document.add(new Paragraph("Issue date: " + formatDate(invoice.getIssuedAt()), normalFont));
        if (invoice.getStatus() == InvoiceStatus.VOID) {
            document.add(new Paragraph("Voided at: " + formatDate(invoice.getVoidedAt()), normalFont));
            addLineIfPresent(document, prefixIfPresent("Void reason: ", invoice.getVoidReason()), normalFont);
        }
        document.add(Chunk.NEWLINE);
    }

    private void addBuyerSection(
            Document document,
            Invoice invoice,
            Font sectionFont,
            Font normalFont
    ) throws Exception {
        document.add(new Paragraph("Bill to:", sectionFont));
        document.add(new Paragraph(invoice.getBuyerName(), normalFont));
        addLineIfPresent(document, invoice.getBuyerAddress(), normalFont);
        addLineIfPresent(document, prefixIfPresent("Tax code: ", invoice.getBuyerTaxCode()), normalFont);
        addLineIfPresent(document, invoice.getBuyerEmail(), normalFont);
        document.add(Chunk.NEWLINE);
    }

    private PdfPTable buildItemsTable(Invoice invoice, Font normalFont) {
        PdfPTable table = new PdfPTable(TABLE_WIDTHS);
        table.setWidthPercentage(100);
        Font headerFont = resolveFont(Font.BOLD, 9);
        for (String header : TABLE_HEADERS) {
            PdfPCell cell = new PdfPCell(new com.lowagie.text.Phrase(header, headerFont));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        invoice.getItems().stream()
                .sorted(Comparator.comparing(
                                InvoiceItem::getSortOrder,
                                Comparator.nullsLast(Integer::compareTo)
                        )
                        .thenComparing(InvoiceItem::getId, Comparator.nullsLast(Long::compareTo)))
                .forEach(item -> {
                    addCell(table, item.getLineType().name(), normalFont, Element.ALIGN_LEFT);
                    addCell(table, item.getDescription(), normalFont, Element.ALIGN_LEFT);
                    addCell(table, item.getQuantity().stripTrailingZeros().toPlainString(), normalFont, Element.ALIGN_RIGHT);
                    addCell(table, formatMoney(item.getUnitPrice()), normalFont, Element.ALIGN_RIGHT);
                    addCell(table, formatMoney(item.getLineSubtotal()), normalFont, Element.ALIGN_RIGHT);
                    addCell(table, formatMoney(item.getDiscountAmount()), normalFont, Element.ALIGN_RIGHT);
                    addCell(table, formatMoney(item.getTaxAmount()), normalFont, Element.ALIGN_RIGHT);
                    addCell(table, formatMoney(item.getLineTotal()), normalFont, Element.ALIGN_RIGHT);
                });
        return table;
    }

    private void addTotalsSection(
            Document document,
            Invoice invoice,
            Font normalFont,
            Font boldFont
    ) throws Exception {
        document.add(Chunk.NEWLINE);
        String currency = invoice.getCurrency();
        document.add(rightAligned("Subtotal: " + formatMoney(invoice.getSubtotal()) + " " + currency, normalFont));
        document.add(rightAligned("Discount: " + formatMoney(invoice.getDiscountTotal()) + " " + currency, normalFont));
        document.add(rightAligned("Tax: " + formatMoney(invoice.getTaxTotal()) + " " + currency, normalFont));
        document.add(rightAligned("Total: " + formatMoney(invoice.getTotalAmount()) + " " + currency, boldFont));
    }

    private void addFooter(Document document, Font mutedFont) throws Exception {
        document.add(Chunk.NEWLINE);
        Paragraph footer = new Paragraph("Thank you for staying with us.", mutedFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }

    private Paragraph rightAligned(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        return paragraph;
    }

    private void addCell(PdfPTable table, String value, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new com.lowagie.text.Phrase(value == null ? "" : value, font));
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private void addLineIfPresent(Document document, String value, Font font) throws Exception {
        if (value != null && !value.isBlank()) {
            document.add(new Paragraph(value, font));
        }
    }

    private String prefixIfPresent(String prefix, String value) {
        return value == null || value.isBlank() ? null : prefix + value;
    }

    private String joinNonBlank(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("  |  ");
            }
            builder.append(value);
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private String settingOrDefault(String key, String defaultValue) {
        String value = hotelSettingsRepository.getStringValue(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String formatDate(OffsetDateTime value) {
        if (value == null) {
            return "-";
        }
        return value.atZoneSameInstant(resolveHotelZone()).format(DATE_FORMAT);
    }

    private ZoneId resolveHotelZone() {
        String configured = hotelSettingsRepository.getStringValue(HotelSettingsService.TIMEZONE_KEY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(configured);
        } catch (DateTimeException exception) {
            return DEFAULT_ZONE;
        }
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal normalized = (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        return String.format(Locale.US, "%,.2f", normalized);
    }

    private Font resolveFont(int style, float size) {
        return FontFactory.getFont(FontFactory.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED, size, style);
    }
}
