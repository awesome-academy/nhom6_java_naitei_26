package com.example.hotelmanagement.dto.payment.sepay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SePayIpnRequest(
        Long timestamp,
        @JsonProperty("notification_type") String notificationType,
        Order order,
        Transaction transaction
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(
            String id,
            @JsonProperty("order_id") String orderId,
            @JsonProperty("order_status") String orderStatus,
            @JsonProperty("order_currency") String orderCurrency,
            @JsonProperty("order_amount") BigDecimal orderAmount,
            @JsonProperty("order_invoice_number") String orderInvoiceNumber
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transaction(
            String id,
            @JsonProperty("transaction_id") String transactionId,
            @JsonProperty("transaction_status") String transactionStatus,
            @JsonProperty("transaction_amount") BigDecimal transactionAmount
    ) {
    }
}
