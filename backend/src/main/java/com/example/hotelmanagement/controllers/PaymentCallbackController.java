package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.payment.PaymentGatewayCallbackRequest;
import com.example.hotelmanagement.services.PaymentCallbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/payments/callback")
public class PaymentCallbackController {

    private final PaymentCallbackService paymentCallbackService;

    public PaymentCallbackController(PaymentCallbackService paymentCallbackService) {
        this.paymentCallbackService = paymentCallbackService;
    }

    @PostMapping(value = "/{provider}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleCallback(
            @PathVariable
            @Size(max = 40)
            @Pattern(regexp = "^[A-Za-z0-9_-]+$")
            String provider,
            @RequestBody String rawPayload,
            HttpServletRequest request
    ) {
        paymentCallbackService.handleCallback(
                provider,
                new PaymentGatewayCallbackRequest(
                        rawPayload,
                        Map.of(
                                "X-Secret-Key",
                                request.getHeader("X-Secret-Key") == null
                                        ? ""
                                        : request.getHeader("X-Secret-Key")
                        )
                ),
                request.getRemoteAddr()
        );
        return ResponseEntity.ok().build();
    }
}
