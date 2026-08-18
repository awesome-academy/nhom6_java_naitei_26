---
title: Invalidate Session On Logout
impact: CRITICAL
impactDescription: ensures that stolen or leaked session tokens cannot be reused after a user has logged out
tags: session, logout, invalidation, security, java
---

## Invalidate Session On Logout

When a user logs out, the session must be destroyed on the server. Simply deleting the cookie on the client is insufficient, as the session remains active on the server and can be hijacked if an attacker possesses the session ID.

**Incorrect (client-side only logout):**

```java
// VULNERABLE: Only deletes cookie, session still exists on server
@GetMapping("/logout")
public String logout(HttpServletResponse response) {
    Cookie cookie = new Cookie("JSESSIONID", null);
    cookie.setMaxAge(0);
    response.addCookie(cookie);
    return "redirect:/login";
}
```

**Correct (server-side session invalidation):**

```java
// 1. Using Standard Servlet API
@GetMapping("/logout")
public String logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
        session.invalidate(); // Destroys the session on the server
    }
    return "redirect:/login";
}

// 2. Using Spring Security (Recommended)
// Configure in SecurityFilterChain:
// http.logout(logout -> logout
//     .logoutUrl("/auth/logout")
//     .invalidateHttpSession(true)
//     .deleteCookies("JSESSIONID")
//     .logoutSuccessUrl("/login")
// );
```

**JWT (Stateless) Logout:**
For JWTs, since they are stateless, you cannot "invalidate" them on the server easily.
- **Option A:** Use short-lived Access Tokens and revoke Refresh Tokens.
- **Option B:** Maintain a "Denylist" in Redis for revoked JTI (JWT ID) claims until they expire.

**Tools:** Spring Security, OWASP ZAP, Manual Audit
