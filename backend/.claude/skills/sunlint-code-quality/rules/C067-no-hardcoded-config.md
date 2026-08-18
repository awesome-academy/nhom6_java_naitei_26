---
title: Do Not Hardcode Configuration Values
impact: HIGH
impactDescription: prevents the need for code changes when environment settings change
tags: configuration, env-vars, java
---

## Do Not Hardcode Configuration Values

Values that change between environments (URLs, thread counts, timeouts) should be stored in configuration files or environment variables.

**Incorrect (hardcoded config):**

```java
public void connect() {
    String url = "https://prod-api.example.com"; // VULNERABLE to env changes
}
```

**Correct (configurable values):**

```java
@Value("${api.url}")
private String apiUrl;

public void connect() {
    // Uses the value from application.properties or ENV
}
```

**Tools:** Spring Boot `@Value` / `@ConfigurationProperties`, Manual Review
