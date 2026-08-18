---
title: Catch Blocks Must Log Root Cause
impact: CRITICAL
impactDescription: ensures debuggability by preserving full exception context
tags: error-handling, logging, debugging, java
---

## Catch Blocks Must Log Root Cause

When catching an exception, you must log the actual exception object (the root cause) along with relevant context. Swallowing exceptions or logging only the message makes it impossible to find the line number or the stack trace of the original error.

**Incorrect (swallowing or incomplete logging):**

```java
try {
    processData();
} catch (Exception e) {
    // VULNERABLE: No stack trace, no context
    log.error("An error occurred");
    
    // VULNERABLE: Swallowed!
}
```

**Correct (logging with context and stack trace):**

```java
try {
    processData(userId);
} catch (IOException e) {
    // SECURE: Log context + the exception object
    log.error("Failed to process data for user: {}", userId, e);
    throw new ServiceException("Database error", e); // Wrap and rethrow
}
```

**Checklist:**
- Always pass the exception object `e` as the last argument to the logger.
- Include unique identifiers (like `userId`, `orderId`) in the log message.
- Avoid logging only `e.getMessage()`.

**Tools:** SonarQube (S1166), SpotBugs, Manual Review
