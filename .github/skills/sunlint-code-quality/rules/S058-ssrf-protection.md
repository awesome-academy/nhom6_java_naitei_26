---
title: Protect Against SSRF Attacks
impact: CRITICAL
impactDescription: prevents attackers from making requests to internal services or external systems from your server
tags: ssrf, validation, security, java
---
## Protect Against SSRF Attacks

Server-Side Request Forgery (SSRF) occurs when an application fetches a resource from a user-supplied URL without validation. Attackers can use this to scan internal networks, access cloud metadata (e.g., `169.254.169.254`), or bypass firewalls.

**Incorrect (trusting user URL):**

```java
@GetMapping("/api/fetch")
public void fetchImage(@RequestParam String url) {
    // VULNERABLE: Attacker input: http://localhost:8080/admin
    // or http://169.254.169.254/latest/meta-data/
    HttpClient.newHttpClient().send(
        HttpRequest.newBuilder().uri(URI.create(url)).build(),
        BodyHandlers.ofString()
    );
}
```

**Correct (allow-listing and validation):**

```java
private static final List<String> ALLOWED_DOMAINS = List.of("cdn.sun-asterisk.vn", "images.example.com");

@GetMapping("/api/fetch")
public void fetchImage(@RequestParam String url) {
    URI uri = URI.create(url);
    
    // 1. Validate Scheme
    if (!"https".equals(uri.getScheme())) {
        throw new SecurityException("Only HTTPS allowed");
    }

    // 2. Validate Domain (Allow-list)
    if (!ALLOWED_DOMAINS.contains(uri.getHost())) {
        throw new SecurityException("Domain not allowed");
    }
    
    // 3. Prohibit internal/private IPs
    // (Additional check against resolving the IP and checking if it's private)
    
    httpClient.send(...);
}
```

**Prevention Strategies:**
- **Allow-listing:** Only allow requests to a small list of known-good domains.
- **Protocol Restriction:** Only allow `https://` (disable `file://`, `gopher://`, `http://`).
- **IP Validation:** Never allow requests to internal IP ranges (127.0.0.1, 10.x.x.x, 172.16.x.x, 192.168.x.x, 169.254.169.254).

**Tools:** OWASP ZAP, Snyk, Manual Architecture Review