---
title: Implement Brute-Force Protection
impact: HIGH
impactDescription: prevents automated attacks from guessing passwords or credentials
tags: brute-force, rate-limiting, authentication, security, java
---
## Implement Brute-Force Protection

Without protection, an attacker can use automated scripts to try thousands of password combinations. You must implement rate limiting or account lockout mechanisms.

**Incorrect (no protection):**

```java
@PostMapping("/login")
public void login(@RequestBody LoginRequest req) {
    // VULNERABLE: No limit on the number of attempts
    boolean success = authService.authenticate(req);
}
```

**Correct (rate limiting and lockout):**

```java
// 1. Using Bucket4j for Rate Limiting
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest req) {
    String clientIp = getClientIp();
    if (loginRateLimiter.tryConsume(clientIp)) {
        // Proceed with auth
        boolean success = authService.authenticate(req);
        // ...
    } else {
        return ResponseEntity.status(429).body("Too many attempts");
    }
}
```

**Tools:** Bucket4j, Spring Security Lockout, Redis