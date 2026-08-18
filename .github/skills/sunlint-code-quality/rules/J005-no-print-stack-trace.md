---
title: Do Not Use printStackTrace(); Use a Logger
impact: MEDIUM
impactDescription: printStackTrace() outputs to stderr, cannot be controlled by log configuration, and may expose sensitive stack traces in production
tags: logging, best-practice, java, security
---

## Do Not Use printStackTrace(); Use a Logger

`e.printStackTrace()` writes directly to `System.err` — it bypasses the logging framework entirely, cannot be filtered, redirected, or formatted by log configuration, and in production environments the output may be lost or may expose sensitive internal details (class names, method names, library versions) to attackers.

**Incorrect (using printStackTrace):**

```java
public void processPayment(Payment payment) {
    try {
        paymentGateway.charge(payment);
    } catch (PaymentException e) {
        e.printStackTrace(); // writes to System.err — uncontrolled output
    }
}

public User findUser(Long id) {
    try {
        return userRepository.findById(id).orElseThrow();
    } catch (Exception e) {
        e.printStackTrace(); // loses structured logging, no context
        return null;
    }
}
```

**Correct (using a logger):**

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaymentService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    public void processPayment(Payment payment) {
        try {
            paymentGateway.charge(payment);
        } catch (PaymentException e) {
            // Log at ERROR with context AND the exception (includes stack trace in log output)
            logger.error("Payment failed for orderId={}, amount={}",
                payment.getOrderId(), payment.getAmount(), e);
            throw new PaymentProcessingException("Payment failed", e);
        }
    }

    public User findUser(Long id) {
        try {
            return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
        } catch (UserNotFoundException e) {
            logger.warn("User lookup failed: userId={}", id, e);
            throw e;
        }
    }
}
```

**Differences between logging levels for exceptions:**

```java
// ERROR: unexpected, system-level failure, requires immediate attention
logger.error("Database connection lost", e);

// WARN: recoverable error, degraded functionality
logger.warn("Payment gateway timeout, retrying: orderId={}", orderId, e);

// INFO: expected business exception (user not found, validation fail)
logger.info("Login attempt failed: username={}", username, e);
```

**Passing the exception as the last argument** to SLF4J/Logback/Log4j2 automatically appends a full stack trace to the log entry — equivalent to calling `printStackTrace()` but through the proper logging pipeline.

**Tools:** PMD (`AvoidPrintStackTrace`), SonarQube (`S1148`), SpotBugs
