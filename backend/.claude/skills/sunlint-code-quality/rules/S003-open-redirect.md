---
title: URL Redirects Must Be In Allow List
impact: MEDIUM
impactDescription: prevents Open Redirect vulnerabilities used in phishing attacks
tags: redirect, phishing, security, java
---

## URL Redirects Must Be In Allow List

Accepting arbitrary URLs for redirection allows attackers to use your trusted domain to trick users into visiting malicious sites (Phishing). Always validate destination URLs against an allow-list.

**Incorrect (arbitrary redirect):**

```java
// VULNERABLE: Attacker input: ?url=http://malicious-site.com
@GetMapping("/api/redirect")
public void handleRedirect(@RequestParam String url, HttpServletResponse response) {
    response.sendRedirect(url);
}
```

**Correct (allow-list validation):**

```java
private static final List<String> ALLOWED_DOMAINS = List.of("sun-asterisk.vn", "partner.com");

@GetMapping("/api/redirect")
public void handleRedirect(@RequestParam String url, HttpServletResponse response) {
    URI uri = URI.create(url);
    if (ALLOWED_DOMAINS.contains(uri.getHost())) {
        response.sendRedirect(url);
    } else {
        throw new SecurityException("Untrusted redirect destination");
    }
}
```

**Tools:** OWASP ZAP, Manual Audit, SonarQube (S5146)
