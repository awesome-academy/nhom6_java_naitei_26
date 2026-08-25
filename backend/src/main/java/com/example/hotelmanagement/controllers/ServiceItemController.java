package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.serviceitem.ServiceItemOptionResponse;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.ServiceItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/service-items")
@PreAuthorize(PermissionExpressions.INVOICE_ISSUE)
@Tag(name = "Service Items", description = "Read active service items available for folio charges.")
public class ServiceItemController {

    private final ServiceItemService serviceItemService;

    public ServiceItemController(ServiceItemService serviceItemService) {
        this.serviceItemService = serviceItemService;
    }

    @GetMapping
    @Operation(summary = "Get Active Service Items")
    public ResponseEntity<List<ServiceItemOptionResponse>> getActiveServiceItems() {
        return ResponseEntity.ok(serviceItemService.getActiveServiceItems());
    }
}
