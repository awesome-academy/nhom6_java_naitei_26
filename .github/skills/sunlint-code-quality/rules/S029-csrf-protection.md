---
title: Apply CSRF Protection
impact: HIGH
impactDescription: prevents Cross-Site Request Forgery attacks that could execute actions on behalf of the user
tags: csrf, security, java
---

## Apply CSRF Protection

CSRF attacks trick a logged-in user into sending a request to your application (e.g., via a hidden form on a malicious site). If the application relies only on cookies for authentication, the browser will include them, and the attack will succeed.

**Incorrect (no CSRF protection):**

```java
// VULNERABLE: Spring Security disabled CSRF
http.csrf(csrf -> csrf.disable());
```

**Correct (enabled and configured CSRF):**

```java
// 1. Spring Security (Enabled by default)
// For SPAs (Stateless/JWT):
// http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));

// 2. In Thymeleaf forms (automatic token insertion):
// <form th:action="@{/logout}" method="post">
```

**Defense Strategies:**
- **Synchronizer Token Pattern:** Include a random token in every state-changing request (POST, PUT, DELETE).
- **SameSite Cookie Attribute:** Set `SameSite=Lax` or `Strict`.
- **Custom Headers:** For AJAX requests, require a custom header (e.g., `X-Requested-With`) which cannot be added cross-site without CORS permission.

**Tools:** Spring Security, OWASP ZAP
