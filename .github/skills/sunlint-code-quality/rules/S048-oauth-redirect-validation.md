---
title: Validate OAuth Redirect URIs Exactly
impact: HIGH
impactDescription: prevents attackers from stealing authorization codes via open redirects
tags: oauth2, redirect, validation, security, java
---
## Validate OAuth Redirect URIs Exactly

An attacker could specify a malicious `redirect_uri` in the authorize request. If the server does not perform exact matching, the authorization code could be sent to the attacker.

**Incorrect (prefix matching):**

```java
// VULNERABLE: Allows redirect to https://your-site.com.attacker.com
if (redirectUri.startsWith("https://your-site.com")) { ... }
```

**Correct (exact matching):**

```java
// SECURE: Exact string comparison against an allow-list
if (ALLOWED_REDIRECT_URIS.contains(redirectUri)) { ... }
```

**Tools:** Spring Security OAuth2