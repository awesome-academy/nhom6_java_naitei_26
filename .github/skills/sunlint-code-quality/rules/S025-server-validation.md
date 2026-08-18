---
title: Always Validate Client Data Server-Side
impact: CRITICAL
impactDescription: prevents malformed data and security bypasses
tags: validation, input, server-side, security, java
---

## Always Validate Client Data Server-Side

Client-side validation (HTML attributes, JavaScript) is for user experience only. It can be easily bypassed by using tools like Postman, `curl`, or browser developer tools. All sensitive data and business logic constraints must be re-validated on the server.

**Incorrect (trusting client input):**

```java
@PostMapping("/api/purchase")
public void purchase(@RequestBody PurchaseRequest req) {
    // VULNERABLE: Assuming price is correct from client
    int total = req.getPrice() * req.getQuantity();
    paymentService.charge(total);
}
```

**Correct (server-side validation):**

```java
// 1. Use Bean Validation (JSR-380)
public class PurchaseRequest {
    @NotNull
    @Min(1)
    private Long productId;

    @Min(1)
    @Max(100)
    private int quantity;
    
    // Do NOT include price in request; fetch it from DB
}

@PostMapping("/api/purchase")
public ResponseEntity<?> purchase(@Valid @RequestBody PurchaseRequest req) {
    // 2. Business logic validation
    Product product = productRepo.findById(req.getProductId())
            .orElseThrow(() -> new ProductNotFoundException());
            
    int total = product.getPrice() * req.getQuantity();
    paymentService.charge(total);
    
    return ResponseEntity.ok().build();
}
```

**Validation Strategies:**
- **Whitelisting:** Only allow known-good input.
- **Strict Typing:** Use appropriate data types (e.g., `Long` for IDs, `BigDecimal` for currency).
- **Constraints:** Use `@NotNull`, `@Size`, `@Pattern` (Regex) in your DTOs.
- **Business Logic:** Validate state transitions (e.g., cannot "Cancel" an already "Shipped" order).

**Tools:** Hibernate Validator, Spring Boot Validation, OWASP ZAP, Manual Review
