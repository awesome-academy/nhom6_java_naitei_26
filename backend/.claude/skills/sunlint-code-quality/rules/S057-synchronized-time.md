---
title: Use Synchronized Time (UTC) In Logs
impact: MEDIUM
impactDescription: enables accurate incident correlation and audit trail reconstruction across distributed systems
tags: logging, time, utc, synchronization, security, java
---
## Use Synchronized Time (UTC) In Logs

Inconsistent timestamps across different servers make it nearly impossible to correlate events during a security incident. Always use UTC and ensure servers are synchronized via NTP.

**Incorrect (local time, inconsistent format):**

```java
// VULNERABLE: Local timezone - inconsistent across distributed servers
log.info("Event at: " + LocalDateTime.now());
```

**Correct (UTC and standardized format):**

```java
// SECURE: Use Instant (UTC) and ISO-8601
log.info("Event at: {}", Instant.now());

// Or configure the logging framework (logback-spring.xml):
// <encoder>
//   <pattern>%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX, UTC} [%thread] %-5level %logger{36} - %msg%n</pattern>
// </encoder>
```

**Checklist:**
- Servers must use NTP (Network Time Protocol) to sync clocks.
- All application logs must use UTC (not server local time).
- Use ISO-8601 format for timestamps.

**Tools:** NTP, Manual Configuration Review