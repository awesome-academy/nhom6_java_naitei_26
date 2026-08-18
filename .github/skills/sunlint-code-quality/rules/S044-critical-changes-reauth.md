---
title: Re-authenticate Before Critical Changes
impact: HIGH
impactDescription: prevents unauthorized sensitive operations if a session is left unattended or hijacked
tags: authentication, reauthentication, security, java
---
## Re-authenticate Before Critical Changes

Critical actions like changing passwords, changing emails, or deleting an account should always require a fresh authentication step (password or MFA challenge).

**Correct (Spring Security):**

```java
// Use @PreAuthorize to ensure user did not use "Remember Me" for this specific method
@PreAuthorize("isFullyAuthenticated()")
@PostMapping("/settings/delete-account")
public void deleteAccount() {
    // ...
}
```

**Definition of "Critical Changes":**
- Security settings (MFA, password).
- Contact information (Email, Phone).
- Financial transactions or withdrawal.
- Account deletion.

**Tools:** Spring Security, OWASP ASVS