---
title: Do Not Log Credentials Or Tokens
impact: CRITICAL
impactDescription: prevents sensitive authentication data from leaking into logs
tags: logging, credentials, secrets, security, java
---

## Do Not Log Credentials Or Tokens

Logging passwords, session tokens, or API keys is a major security violation. Logs are often stored with less security than the actual database and can be accessed by many developers or automated log aggregation tools.

**Incorrect (logging sensitive data):**

```java
@PostMapping("/login")
public void login(@RequestBody LoginRequest req) {
    // VULNERABLE: Password written to logs!
    log.info("Login attempt for user: {} with password: {}", req.getUsername(), req.getPassword());
}
```

**Correct (safely logging):**

```java
@PostMapping("/login")
public void login(@RequestBody LoginRequest req) {
    // SECURE: Only log the username or a masked value
    log.info("Login attempt for user: {}", req.getUsername());
}
```

**Masking Strategy:**
If you must log sensitive objects, use a custom `toString()` method or a library to mask fields:
`{ "user": "admin", "password": "****" }`

**Tools:** Logback/Log4j2 filters, Manual Review, SonarQube (S2254)
