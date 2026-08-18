---
title: TLS Encryption For All Connections
impact: CRITICAL
impactDescription: protects data in transit from interception and tampering
tags: tls, encryption, https, transport, security, java
---

## TLS Encryption For All Connections

All network communications, whether between the client and server or between internal services, must be encrypted using TLS. Unencrypted connections (HTTP, raw JDBC) allow attackers to perform Man-in-the-Middle (MitM) attacks to steal sensitive data.

**Incorrect (unencrypted connections):**

```java
// VULNERABLE: Using HTTP instead of HTTPS
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("http://api.production.sun-asterisk.vn/data"))
    .build();

// VULNERABLE: Unencrypted database connection
String url = "jdbc:postgresql://db.sun-asterisk.vn:5432/mydb";
```

**Correct (TLS everywhere):**

```java
// 1. HTTPS for all external API calls
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.production.sun-asterisk.vn/data"))
    .build();

// 2. TLS for Database connections
String url = "jdbc:postgresql://db.sun-asterisk.vn:5432/mydb?ssl=true";

// 3. Enabling HSTS to force browsers to use HTTPS
// In Spring Security:
// http.headers(headers -> headers
//     .httpStrictTransportSecurity(hsts -> hsts
//         .includeSubDomains(true)
//         .maxAgeInSeconds(31536000)
//     )
// );

// 4. Redirecting HTTP to HTTPS
// http.requiresChannel(channel -> channel
//     .anyRequest().requiresSecure()
// );
```

**Requirements:**
- All endpoints must strictly use HTTPS.
- Plain HTTP requests must be redirected to HTTPS.
- Use HSTS (`Strict-Transport-Security`) headers to prevent protocol downgrade attacks.
- Ensure internal service-to-service communication is also encrypted.

**Tools:** OWASP ZAP, SSLyze, Qualys SSL Labs, Manual Review
