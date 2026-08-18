---
title: Set HttpOnly On Session Cookies
impact: HIGH
impactDescription: prevents client-side scripts from accessing the cookie, mitigating XSS impacts
tags: cookie, httponly, session, xss, security, java
---
## Set HttpOnly On Session Cookies

The `HttpOnly` flag prevents JavaScript from accessing the cookie via `document.cookie`. This is a critical defense-in-depth measure; even if an attacker finds an XSS vulnerability, they cannot steal the session cookie.

**Incorrect (accessible via JS):**

```java
// VULNERABLE: JS can read this cookie
Cookie cookie = new Cookie("SESSION_ID", "12345");
response.addCookie(cookie);
```

**Correct (HttpOnly cookie):**

```java
// 1. Manual Servlet API
Cookie cookie = new Cookie("SESSION_ID", "12345");
cookie.setHttpOnly(true); // SECURE: Inaccessible to JavaScript
response.addCookie(cookie);

// 2. Spring Boot Configuration (application.properties)
// server.servlet.session.cookie.http-only=true
```

**Tools:** Browser DevTools, SonarQube (S3330)