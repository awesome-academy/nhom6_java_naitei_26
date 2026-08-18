---
title: Set SameSite On Session Cookies
impact: MEDIUM
impactDescription: prevents Cross-Site Request Forgery (CSRF) attacks by restricting cookie transmission
tags: cookie, samesite, csrf, security, java
---
## Set SameSite On Session Cookies

The `SameSite` attribute tells the browser whether to send the cookie with cross-site requests. Setting it to `Lax` or `Strict` significantly reduces the risk of CSRF attacks.

**Incorrect (no SameSite attribute):**

```java
// VULNERABLE: Browser defaults vary, might allow cross-site sending
Cookie cookie = new Cookie("SESSION_ID", "12345");
response.addCookie(cookie);
```

**Correct (SameSite Lax/Strict):**

```java
// 1. Spring Boot Configuration (Recommended)
// server.servlet.session.cookie.same-site=lax

// 2. Spring Security (if using SessionRepository)
// @Bean
// public CookieSerializer cookieSerializer() {
//     DefaultCookieSerializer serializer = new DefaultCookieSerializer();
//     serializer.setSameSite("Lax");
//     return serializer;
// }

// 3. Manual Header (Servlet 6.0+ or via Filter)
response.setHeader("Set-Cookie", "SESSION_ID=12345; Secure; HttpOnly; SameSite=Lax");
```

**Modes:**
- `Strict`: Cookie only sent for first-party requests.
- `Lax`: Cookie sent for first-party and safe top-level navigations (links). **Recommended default.**
- `None`: Cookie sent always (requires `Secure` attribute).

**Tools:** OWASP ZAP, Browser DevTools