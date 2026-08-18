---
title: Use Custom Error Classes
impact: MEDIUM
impactDescription: enables specific error handling and cleaner code structure
tags: error-handling, clean-code, exceptions, java
---

## Use Custom Error Classes

Throwing generic exceptions like `RuntimeException` or `Exception` forces the caller to use "catch-all" blocks, which is dangerous and lacks semantic meaning. Custom exceptions allow for fine-grained error handling.

**Incorrect (generic exceptions):**

```java
public void process(int amount) {
    if (amount < 0) {
        throw new RuntimeException("Invalid amount");
    }
}
```

**Correct (custom exceptions):**

```java
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}

public void process(int amount) {
    if (amount < 0) {
        throw new InsufficientFundsException("Amount cannot be negative");
    }
}

// Caller can now catch specifically
try {
    service.process(-1);
} catch (InsufficientFundsException e) {
    // Handle specifically
}
```

**Recommendation:**
- Inherit from `RuntimeException` for unrecoverable errors (unchecked).
- Inherit from `Exception` for errors that the caller *must* handle (checked).
- Use descriptive names (e.g., `UserNotFoundException`, `DatabaseConnectionException`).

**Tools:** IntelliJ Inspections, Manual Review
