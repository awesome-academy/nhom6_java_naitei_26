---
title: Re-authenticate For Long-lived Sessions
impact: MEDIUM
impactDescription: ensures continuous user identity verification and reduces the window for session hijacking
tags: session, authentication, timeout, security, java
---
## Re-authenticate For Long-lived Sessions

Long-lived sessions (e.g., "Remember Me" for 30 days) increase the risk of session hijacking. For sensitive actions, you should require the user to re-enter their password even if they are already "logged in."

**Incorrect (never re-auth):**

```java
@PostMapping("/user/change-email")
public void changeEmail(@RequestBody String newEmail) {
    // VULNERABLE: If the computer was left unlocked, anyone can change the email
    userService.updateEmail(currentUserId, newEmail);
}
```

**Correct (fresh authentication):**

```java
@PostMapping("/user/change-email")
public ResponseEntity<?> changeEmail(@RequestBody ChangeEmailRequest req) {
    // SECURE: Verify the password again for this critical action
    if (!authService.verifyPassword(currentUserId, req.getPassword())) {
        return ResponseEntity.status(401).body("Password verification failed");
    }
    
    userService.updateEmail(currentUserId, req.getNewEmail());
    return ResponseEntity.ok().build();
}
```

**Tools:** Spring Security (IsFullyAuthenticated), Manual Audit