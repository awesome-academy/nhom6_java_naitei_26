---
title: Never Use Default Credentials
impact: CRITICAL
impactDescription: prevents easy access to system resources for attackers using widely known credentials
tags: authentication, credentials, default, security, java
---

## Never Use Default Credentials

Using default usernames and passwords (like `admin`/`admin`, `root`/`password`) for databases, servers, or application accounts is a major security risk. Attackers use automated tools to try these combinations across the internet.

**Incorrect (default values in code or config):**

```java
// DANGEROUS: Default credentials in application code
String dbUser = "admin";
String dbPass = "password123";

// In config file (application.properties):
spring.datasource.password=root 
```

**Correct (environment-based configuration):**

```java
// SECURE: Retrieve from Environment Variables or Secrets Manager
@Value("${DB_USER}")
private String dbUser;

@Value("${DB_PASSWORD}")
private String dbPass;
```

**Hardening Rules:**
- Forced change of default passwords on the first login.
- Disable default accounts (like `guest`) if not needed.
- Monitor for login attempts using common default usernames.

**Tools:** Manual Review, Security Audit
