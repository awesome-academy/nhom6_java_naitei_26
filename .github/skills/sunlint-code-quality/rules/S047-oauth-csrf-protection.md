---
title: Protect OAuth Code Flow Vs CSRF
impact: HIGH
impactDescription: prevents attackers from linking their accounts to a victim's session
tags: oauth2, csrf, state, security, java
---
## Protect OAuth Code Flow Vs CSRF

In the OAuth2 Authorization Code flow, you must use the `state` parameter to prevent CSRF. The `state` parameter ensures that the response from the Authorization Server (AS) matches the original request initiated by the user.

**Incorrect (missing state):**

```java
// VULNERABLE: No state parameter
String redirectUrl = "https://auth-server.com/authorize?client_id=123&response_type=code";
```

**Correct (using state):**

```java
// SECURE: Generate and verify state
String state = generateSecureRandomString();
session.setAttribute("oauth_state", state);
String redirectUrl = "https://auth-server.com/authorize?client_id=123&response_type=code&state=" + state;

// In the callback:
String returnedState = request.getParameter("state");
if (!state.equals(returnedState)) {
    throw new SecurityException("CSRF Detected");
}
```

**Tools:** Spring Security OAuth2 (handles this automatically)