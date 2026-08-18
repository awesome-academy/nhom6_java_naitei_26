---
title: Set Anti-cache Headers For Sensitive Pages
impact: MEDIUM
impactDescription: prevents sensitive data from being stored in browser or proxy caches
tags: cache, headers, security, java
---
## Set Anti-cache Headers For Sensitive Pages

Sensitive information (bank statements, health records) should not be cached on the user's computer or on intermediate proxies. If cached, another user of the same computer or network could potentially see the data.

**Incorrect (cacheable sensitive data):**

```java
@GetMapping("/account/balance")
public ResponseEntity<BalanceDto> getBalance() {
    return ResponseEntity.ok(service.getBalance());
}
```

**Correct (anti-cache headers):**

```java
@GetMapping("/account/balance")
public ResponseEntity<BalanceDto> getBalance() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(service.getBalance());
}
```

**Recommended Headers:**
- `Cache-Control: no-store, no-cache, must-revalidate, max-age=0`
- `Pragma: no-cache`
- `Expires: 0`

**Tools:** Browser DevTools, OWASP ZAP