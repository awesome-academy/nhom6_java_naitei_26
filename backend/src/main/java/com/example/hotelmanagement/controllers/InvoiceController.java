package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.invoice.InvoiceAdjustmentRequest;
import com.example.hotelmanagement.dto.invoice.InvoiceResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/invoices/{invoicePublicId}")
    public ResponseEntity<InvoiceResponse> getInvoice(
            @PathVariable String invoicePublicId
    ) {
        return ResponseEntity.ok(invoiceService.getInvoice(invoicePublicId));
    }

    @GetMapping("/bookings/{bookingPublicId}/invoices/draft")
    public ResponseEntity<InvoiceResponse> getDraftByBooking(
            @PathVariable String bookingPublicId
    ) {
        return ResponseEntity.ok(invoiceService.getDraftByBooking(bookingPublicId));
    }

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

    @DeleteMapping("/invoices/{invoicePublicId}/adjustments/{itemId}")
    public ResponseEntity<InvoiceResponse> removeAdjustment(
            @PathVariable String invoicePublicId,
            @PathVariable Long itemId
    ) {
        return ResponseEntity.ok(invoiceService.removeAdjustment(invoicePublicId, itemId));
    }
}
