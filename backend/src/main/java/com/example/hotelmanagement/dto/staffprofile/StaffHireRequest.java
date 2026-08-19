package com.example.hotelmanagement.dto.staffprofile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "StaffHireRequest", description = "Payload to hire a staff member. Creates the user account if it does not exist yet.")
public record StaffHireRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 150)
        String fullName,

        @Size(max = 20)
        @Pattern(regexp = "^[0-9+() .-]*$")
        String phone,

        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$")
        String employeeCode,

        @NotBlank
        @Size(max = 80)
        String position,

        @Size(max = 80)
        String department,

        @PastOrPresent
        LocalDate hiredAt,

        @DecimalMin("0.00")
        @Digits(integer = 12, fraction = 2)
        BigDecimal baseSalary
) {
}
