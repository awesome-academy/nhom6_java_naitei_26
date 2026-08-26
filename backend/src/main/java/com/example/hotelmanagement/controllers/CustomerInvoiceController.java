package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.invoice.InvoicePdfResponse;
import com.example.hotelmanagement.dto.invoice.InvoiceResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.InvoicePdfService;
import com.example.hotelmanagement.services.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/bookings/{bookingPublicId}", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Customer Invoices", description = "Read issued invoices owned by the current customer.")
public class CustomerInvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;

    public CustomerInvoiceController(
            InvoiceService invoiceService,
            InvoicePdfService invoicePdfService
    ) {
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
    }

    @Operation(summary = "Get customer-visible invoice for a booking")
    @GetMapping("/invoice")
    @PreAuthorize(PermissionExpressions.BOOKING_READ_OWN)
    public ResponseEntity<InvoiceResponse> getInvoice(
            @PathVariable String bookingPublicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(invoiceService.getCustomerInvoice(
                bookingPublicId,
                principal.getId()
        ));
    }

    @Operation(summary = "Get customer invoice PDF download URL")
    @GetMapping("/invoices/{invoicePublicId}/pdf")
    @PreAuthorize(PermissionExpressions.BOOKING_READ_OWN)
    public ResponseEntity<InvoicePdfResponse> getPdf(
            @PathVariable String bookingPublicId,
            @PathVariable String invoicePublicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(invoicePdfService.getCustomerDownloadUrl(
                bookingPublicId,
                invoicePublicId,
                principal.getId()
        ));
    }
}
