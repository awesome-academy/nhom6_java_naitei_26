package com.example.hotelmanagement.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.hotelmanagement.dto.foliocharge.FolioChargeCreateRequest;
import com.example.hotelmanagement.dto.foliocharge.FolioChargeResponse;
import com.example.hotelmanagement.dto.foliocharge.FolioChargeVoidRequest;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.FolioChargeService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(
        value = "/api/bookings/{bookingPublicId}/folio-charges",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@PreAuthorize(PermissionExpressions.INVOICE_ISSUE)
@Tag(name = "Folio Charges", description = "Record and void stay-related service charges.")
public class FolioChargeController {

    private final FolioChargeService folioChargeService;

    public FolioChargeController(FolioChargeService folioChargeService) {
        this.folioChargeService = folioChargeService;
    }

    @Operation(summary = "Get Folio Charges")
    @GetMapping
    public ResponseEntity<List<FolioChargeResponse>> getFolioCharges(
            @PathVariable String bookingPublicId
    ) {
        return ResponseEntity.ok(folioChargeService.getFolioCharges(bookingPublicId));
    }

    @Operation(summary = "Create Folio Charge")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FolioChargeResponse> createFolioCharge(
            @PathVariable String bookingPublicId,
            @Valid @RequestBody FolioChargeCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        FolioChargeResponse response = folioChargeService.createFolioCharge(
                bookingPublicId,
                request,
                principal.getId()
        );
        URI location = URI.create(
                "/api/bookings/" + response.bookingPublicId()
                        + "/folio-charges/" + response.id()
        );
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Void Folio Charge")
    @PatchMapping(value = "/{chargeId}/void", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FolioChargeResponse> voidFolioCharge(
            @PathVariable String bookingPublicId,
            @PathVariable Long chargeId,
            @Valid @RequestBody FolioChargeVoidRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(folioChargeService.voidFolioCharge(
                bookingPublicId,
                chargeId,
                request,
                principal.getId()
        ));
    }
}
