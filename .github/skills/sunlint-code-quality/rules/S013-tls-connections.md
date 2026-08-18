---
title: Always Use TLS For All Connections
impact: HIGH
impactDescription: protects data in transit from eavesdropping and tampering
tags: tls, https, encryption, transport, security, java
---

## Always Use TLS For All Connections

Transmitting data over unencrypted HTTP, JDBC, or Redis connections exposes sensitive information to everyone on the network path. All connections in production must use TLS 1.2 or higher.

**Incorrect (unencrypted connections):**

```java
// VULNERABLE: Using HTTP API
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("http://api.internal.com/data"))
    .build();

// VULNERABLE: Unencrypted JDBC
String url = "jdbc:postgresql://db.server:5432/mydb";
```

**Correct (TLS/SSL everywhere):**

```java
// 1. HTTPS for all APIs
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.internal.com/data"))
    .build();

// 2. TLS for Database
String url = "jdbc:postgresql://db.server:5432/mydb?ssl=true&sslmode=verify-full";

// 3. Redis with TLS (Jedis/Lettuce)
RedisClient client = RedisClient.create("rediss://localhost:6380"); // Note: rediss://
```

**Tools:** SSLyze, Qualys SSL Labs, Snyk, Manual Review
