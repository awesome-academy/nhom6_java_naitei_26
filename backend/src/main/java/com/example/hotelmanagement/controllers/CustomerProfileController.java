package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.customerprofile.CustomerProfileCreateRequest;
import com.example.hotelmanagement.dto.customerprofile.CustomerProfileResponse;
import com.example.hotelmanagement.dto.customerprofile.CustomerProfileUpdateRequest;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.CustomerProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/customer-profiles/me", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerProfileController {

    private final CustomerProfileService customerProfileService;

    public CustomerProfileController(CustomerProfileService customerProfileService) {
        this.customerProfileService = customerProfileService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerProfileResponse createOwnProfile(
            @Valid @RequestBody CustomerProfileCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return customerProfileService.createOwnProfile(principal.getId(), request);
    }

    @GetMapping
    public ResponseEntity<CustomerProfileResponse> getOwnProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(customerProfileService.getOwnProfile(principal.getId()));
    }

    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CustomerProfileResponse> updateOwnProfile(
            @Valid @RequestBody CustomerProfileUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(customerProfileService.updateOwnProfile(principal.getId(), request));
    }

    @DeleteMapping
    public ResponseEntity<Void> deactivateOwnAccount(@AuthenticationPrincipal UserPrincipal principal) {
        customerProfileService.deactivateOwnAccount(principal.getId());
        return ResponseEntity.noContent().build();
    }
}
