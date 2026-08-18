---
title: Preserve Stack Trace When Rethrowing Exceptions
impact: HIGH
impactDescription: discarding the original exception's stack trace makes debugging nearly impossible — the root cause is permanently lost
tags: error-handling, best-practice, java, debugging
---

## Preserve Stack Trace When Rethrowing Exceptions

When catching an exception and throwing a new one, always pass the original exception as the **cause**. If you only rethrow a new exception with `e.getMessage()` or no cause at all, the original stack trace — which shows where the error actually originated — is permanently discarded.

**Incorrect (losing the stack trace):**

```java
public void processOrder(Order order) throws OrderException {
    try {
        paymentService.charge(order.getPayment());
    } catch (PaymentException e) {
        // Message-only: root cause stack trace is lost
        throw new OrderException("Payment failed: " + e.getMessage());
    }
}

public User loadUser(Long id) throws ServiceException {
    try {
        return userRepository.findById(id).orElseThrow();
    } catch (Exception e) {
        // No cause: impossible to trace back to the original error
        throw new ServiceException("User not found");
    }
}

public void sendEmail(String to, String body) {
    try {
        emailClient.send(to, body);
    } catch (EmailException e) {
        logger.error("Email failed");  // No exception logged — stack trace lost
        throw new RuntimeException("Email failed"); // No cause chained
    }
}
```

**Correct (preserving the stack trace):**

```java
public void processOrder(Order order) throws OrderException {
    try {
        paymentService.charge(order.getPayment());
    } catch (PaymentException e) {
        // Pass the original exception as the cause
        throw new OrderException("Payment failed for orderId=" + order.getId(), e);
    }
}

public User loadUser(Long id) throws ServiceException {
    try {
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found: id=" + id));
    } catch (UserNotFoundException e) {
        throw new ServiceException("User lookup failed: id=" + id, e); // chained
    }
}

public void sendEmail(String to, String body) {
    try {
        emailClient.send(to, body);
    } catch (EmailException e) {
        logger.error("Email sending failed: recipient={}", to, e); // log with cause
        throw new NotificationException("Email failed", e);        // chain the cause
    }
}
```

**Using initCause for exceptions without cause constructors:**

```java
try {
    someOperation();
} catch (SomeException e) {
    RuntimeException wrapped = new RuntimeException("Operation failed");
    wrapped.initCause(e); // alternative to constructor argument
    throw wrapped;
}
```

**Inspecting cause chains:**

```java
// Cause chain is accessible at runtime
try {
    processOrder(order);
} catch (OrderException e) {
    Throwable cause = e.getCause(); // retrieves PaymentException
    logger.error("Root cause: {}", cause.getMessage());
}
```

**Adding suppressed exceptions (try-with-resources pattern):**

```java
// When closing a resource also throws — use addSuppressed
Exception primary = null;
try {
    doWork();
} catch (Exception e) {
    primary = e;
    throw e;
} finally {
    try {
        resource.close();
    } catch (Exception closeEx) {
        if (primary != null) {
            primary.addSuppressed(closeEx); // preserves both
        }
    }
}
```

**Tools:** PMD (`PreserveStackTrace`), SpotBugs (`REC_CATCH_EXCEPTION`), SonarQube (`S1166`)
