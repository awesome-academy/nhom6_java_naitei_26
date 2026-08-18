---
title: Return Generic Error Messages To Users
impact: MEDIUM
impactDescription: prevents information disclosure that could help attackers map the system
tags: error-handling, security, java
---
## Return Generic Error Messages To Users

Avoid leaking system details (stack traces, DB versions) in HTTP responses.

**Correct (Spring Security Handler):**

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<String> handle(Exception e) {
    log.error("Internal Error", e);
    return ResponseEntity.status(500).body("An internal error occurred.");
}
```

**Tools:** Spring Boot ControllerAdvice