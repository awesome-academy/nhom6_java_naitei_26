---
title: Use __Host- Prefix For Cookies
impact: MEDIUM
impactDescription: prevents cookie tossing and domain-shadowing attacks
tags: cookie, host-prefix, security, java
---

## Use __Host- Prefix For Cookies

The `__Host-` prefix on a cookie name provides maximum security. It forces the cookie to be `Secure`, have no specified `Domain` (preventing subdomains from accessing it), and be restricted to the same path that set it (`/`).

**Incorrect (standard cookie name):**

```java
// VULNERABLE: Can be shadowed by subdomains
Cookie cookie = new Cookie("SESSION_ID", "12345");
```

**Correct (__Host- prefix):**

```java
// SECURE: Browser enforces Secure, Path=/, and no Domain
Cookie cookie = new Cookie("__Host-SESSION_ID", "12345");
cookie.setSecure(true);
cookie.setPath("/");
response.addCookie(cookie);
```

**Requirements:**
- The cookie name must start with `__Host-`.
- The `Secure` attribute must be set.
- The `Path` attribute must be `/`.
- The `Domain` attribute must **NOT** be set.

**Tools:** Browser DevTools, Manual Review
