package com.example.hotelmanagement.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.hotelmanagement.dto.user.UserResponse;
import com.example.hotelmanagement.dto.user.UserUpdateRequest;
import com.example.hotelmanagement.dto.user.CustomerBookingResponse;
import com.example.hotelmanagement.dto.user.CustomerDetailResponse;
import com.example.hotelmanagement.dto.user.CustomerListResponse;
import com.example.hotelmanagement.dto.user.CustomerStatusUpdateRequest;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.security.UserPrincipal;
import com.example.hotelmanagement.services.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@RequestMapping(value = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "Users", description = "Manage user accounts. Administrative access is required.")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get Users")
    @GetMapping
    public ResponseEntity<CustomerListResponse> getUsers(
        @RequestParam(defaultValue = "CUSTOMER")
        @Pattern(regexp = "CUSTOMER", flags = Pattern.Flag.CASE_INSENSITIVE)
        String role,
        @RequestParam(required = false) UserStatus status,
        @RequestParam(defaultValue = "") @Size(max = 100) String search,
        @RequestParam(defaultValue = "0") @Min(0) int page
    ) {
        if (!"CUSTOMER".equalsIgnoreCase(role)) {
            throw new BusinessValidationException("Only CUSTOMER users are available in this endpoint");
        }
        return ResponseEntity.ok(userService.getCustomers(status, search, page));
    }

    @Operation(summary = "Get User")
    @GetMapping("/{publicId}")
    public ResponseEntity<CustomerDetailResponse> getUser(@PathVariable String publicId) {
        return ResponseEntity.ok(userService.getCustomer(publicId));
    }

    @Operation(summary = "Get Customer Bookings")
    @GetMapping("/{publicId}/bookings")
    public ResponseEntity<List<CustomerBookingResponse>> getCustomerBookings(@PathVariable String publicId) {
        return ResponseEntity.ok(userService.getCustomerBookings(publicId));
    }

    @Operation(summary = "Update Customer Status")
    @PatchMapping(value = "/{publicId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponse> updateCustomerStatus(
        @PathVariable String publicId,
        @Valid @RequestBody CustomerStatusUpdateRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(userService.updateCustomerStatus(publicId, request, principal.getId()));
    }

    @Operation(summary = "Update User")
    @PatchMapping(value = "/{publicId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponse> updateUser(
        @PathVariable String publicId,
        @Valid @RequestBody UserUpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateUser(publicId, request));
    }

    @Operation(summary = "Delete User")
    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String publicId) {
        userService.deleteUser(publicId);
        return ResponseEntity.noContent().build();
    }
}
