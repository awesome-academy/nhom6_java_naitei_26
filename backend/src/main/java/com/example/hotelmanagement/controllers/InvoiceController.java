package com.example.hotelmanagement.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.hotelmanagement.dto.invoice.InvoiceAdjustmentRequest;
import com.example.hotelmanagement.dto.invoice.InvoicePdfResponse;
import com.example.hotelmanagement.dto.invoice.InvoiceResponse;
import com.example.hotelmanagement.dto.invoice.InvoiceVoidRequest;
import com.example.hotelmanagement.dto.invoice.InvoiceVoidResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.InvoicePdfService;
import com.example.hotelmanagement.services.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize(PermissionExpressions.INVOICE_ISSUE)
@Tag(name = "Invoices", description = "Manage draft invoice details and invoice lookup.")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;

    public InvoiceController(InvoiceService invoiceService, InvoicePdfService invoicePdfService) {
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
    }

    @GetMapping("/invoices/{invoicePublicId}/pdf")
    public ResponseEntity<InvoicePdfResponse> getPdf(@PathVariable String invoicePublicId) {
        return ResponseEntity.ok(invoicePdfService.getDownloadUrl(invoicePublicId));
    }

    @PostMapping("/invoices/{invoicePublicId}/issue")
    public ResponseEntity<InvoiceResponse> issue(
            @PathVariable String invoicePublicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(invoiceService.issue(invoicePublicId, principal.getId()));
    }

    @PostMapping(value = "/invoices/{invoicePublicId}/void", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(PermissionExpressions.INVOICE_VOID)
    public ResponseEntity<InvoiceVoidResponse> voidInvoice(
            @PathVariable String invoicePublicId,
            @Valid @RequestBody InvoiceVoidRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(invoiceService.voidInvoice(invoicePublicId, principal.getId(), request));
    }

    @Operation(summary = "Get Invoice")
    @GetMapping("/invoices/{invoicePublicId}")
    public ResponseEntity<InvoiceResponse> getInvoice(
            @PathVariable String invoicePublicId
    ) {
        return ResponseEntity.ok(invoiceService.getInvoice(invoicePublicId));
    }

    @Operation(summary = "Get Draft By Booking")
    @GetMapping("/bookings/{bookingPublicId}/invoices/draft")
    public ResponseEntity<InvoiceResponse> getDraftByBooking(
            @PathVariable String bookingPublicId
    ) {
        return ResponseEntity.ok(invoiceService.getDraftByBooking(bookingPublicId));
    }

    @Operation(summary = "Add Adjustment")
    @PostMapping(
            value = "/invoices/{invoicePublicId}/adjustments",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<InvoiceResponse> addAdjustment(
            @PathVariable String invoicePublicId,
            @Valid @RequestBody InvoiceAdjustmentRequest request
    ) {
        return ResponseEntity.ok(invoiceService.addAdjustment(invoicePublicId, request));
    }

    @Operation(summary = "Remove Adjustment")
    @DeleteMapping("/invoices/{invoicePublicId}/adjustments/{itemId}")
    public ResponseEntity<InvoiceResponse> removeAdjustment(
            @PathVariable String invoicePublicId,
            @PathVariable Long itemId
    ) {
        return ResponseEntity.ok(invoiceService.removeAdjustment(invoicePublicId, itemId));
    }
}
