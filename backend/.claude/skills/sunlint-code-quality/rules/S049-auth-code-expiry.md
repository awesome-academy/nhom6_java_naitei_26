---
title: Authentication Codes Must Expire Quickly
impact: HIGH
impactDescription: limits the window of opportunity for an attacker to use a stolen code
tags: oauth2, authentication, expiry, security, java
---
## Authentication Codes Must Expire Quickly

Authorization codes (and OTPs) are intended for immediate, one-time use. They should have a very short lifespan (typically 1 to 10 minutes).

**Correct (Spring Security):**

```java
// Configure the token store or code service to expire codes
// Default in Spring Security OAuth2 is usually 5 minutes
```

**Best Practice:**
- Max lifespan: **10 minutes**.
- Revoke code immediately after use (One-time use).
- Revoke all associated tokens if a code is used twice (indicates an attack).

**Tools:** Spring Security, Redis (for TTL)