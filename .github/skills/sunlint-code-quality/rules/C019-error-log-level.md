---
title: Do Not Use Error Log Level For Non-Critical Issues
impact: MEDIUM
impactDescription: prevents log noise and ensures relevant alerts
tags: logging, best-practice, java
---

## Do Not Use Error Log Level For Non-Critical Issues

The `ERROR` level should be reserved for issues that require immediate developer attention. For expected failures (like invalid user input), use `WARN` or `INFO`.

**Incorrect (error for everything):**

```java
if (user == null) {
    log.error("User not found: {}", id); // Should be WARN or INFO
}
```

**Correct (appropriate log levels):**

```java
if (user == null) {
    log.warn("Attempt to access non-existent user: {}", id);
}

try {
    db.save(data);
} catch (SQLException e) {
    log.error("Critical database failure", e); // TRUE ERROR
}
```

**Tools:** Manual Review
