---
title: Enforce Authorization At Server Side
impact: CRITICAL
impactDescription: prevents unauthorized access to sensitive data and functionality
tags: authorization, security, server-side, access-control, java
---

## Enforce Authorization At Server Side

Client-side checks (hiding buttons, disabling links) are purely for UI/UX. The final decision on whether a user can perform an action must always happen on the server. Otherwise, an attacker can simply call the API endpoint directly.

**Incorrect (client-side or perimeter only):**

```java
// VULNERABLE: Assuming anyone hitting this URL is an admin
@GetMapping("/admin/delete-user")
public void deleteUser(@RequestParam Long id) {
    userRepo.deleteById(id);
}
```

**Correct (server-side checks):**

```java
// 1. Using Spring Security Annotations
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/api/users/{id}/delete")
public ResponseEntity<?> deleteUser(@PathVariable Long id) {
    userRepo.deleteById(id);
    return ResponseEntity.ok().build();
}

// 2. Resource-level authorization (Owning record check)
@PostMapping("/api/posts/{id}/edit")
public ResponseEntity<?> editPost(@PathVariable Long id, @RequestBody PostUpdateData data) {
    Post post = postRepo.findById(id).orElseThrow();
    
    // Explicitly check if the current user is the author
    if (!post.getAuthorId().equals(CurrentContext.getUserId())) {
        return ResponseEntity.status(403).body("Forbidden");
    }
    
    post.update(data);
    return ResponseEntity.ok().build();
}
```

**Security Best Practices:**
- Follow the **Principle of Least Privilege**.
- Default to **Deny All** and explicitly allow access to specific roles/users.
- Perform authorization checks for every single request, even for subsequent steps in a multi-step process.

**Tools:** Spring Security, Apache Shiro, Manual Audit
