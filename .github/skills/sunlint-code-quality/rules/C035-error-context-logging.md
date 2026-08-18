---
title: Log All Relevant Context On Errors
impact: CRITICAL
impactDescription: enables efficient incident resolution by providing necessary details
tags: logging, error-handling, java
---

## Log All Relevant Context On Errors

An error log without context (like IDs or state) is often useless. Always include enough information to reproduce the issue.

**Incorrect (lacking context):**

```java
try {
    paymentService.charge(amount);
} catch (Exception e) {
    log.error("Payment failed", e); // Which user? Which order?
}
```

**Correct (contextual logging):**

```java
try {
    paymentService.charge(orderId, amount);
} catch (Exception e) {
    log.error("Payment failed for Order: {} (User: {}). Amount: {}", 
              orderId, userId, amount, e);
}
```

**Recommended Data to Log:**
- Entity IDs (`userId`, `orderId`).
- Failed values (if not sensitive).
- Correlation IDs (`traceId`).

**Tools:** SLF4J (MDC - Mapped Diagnostic Context), Sentry