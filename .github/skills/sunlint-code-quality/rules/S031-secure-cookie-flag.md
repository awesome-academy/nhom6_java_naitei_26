---
title: Set Secure Flag On Session Cookies
impact: HIGH
impactDescription: prevents cookies from being sent over unencrypted HTTP connections
tags: cookie, secure, session, transport, security, java
---
## Set Secure Flag On Session Cookies

The `Secure` flag ensures that the browser only sends the cookie over encrypted (HTTPS) connections. Without this flag, a cookie could be sent over a plain HTTP link (e.g., if a user manually types `http://...`), making it vulnerable to interception.

**Incorrect (insecure cookie):**

```java
// VULNERABLE: Cookie can be sent over HTTP
Cookie cookie = new Cookie("SESSION_ID", "12345");
cookie.setPath("/");
response.addCookie(cookie);
```

**Correct (secure cookie):**

```java
// 1. Manual Servlet API
Cookie cookie = new Cookie("SESSION_ID", "12345");
cookie.setSecure(true); // SECURE: Only send over HTTPS
cookie.setPath("/");
response.addCookie(cookie);

// 2. Spring Boot Configuration (application.properties)
// server.servlet.session.cookie.secure=true

// 3. Spring Security Header
// http.headers(headers -> headers
//     .contentSecurityPolicy(csp -> csp.policyDirectives("upgrade-insecure-requests"))
// );
```

**Tools:** OWASP ZAP, Browser DevTools, SonarQube (S2255)