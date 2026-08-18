---
title: Protect Against Log Injection
impact: MEDIUM
impactDescription: prevents attackers from forged log entries and corrupting audit trails
tags: logging, injection, log-injection, security, java
---
## Protect Against Log Injection

Log injection occurs when user-controlled data is written to a log file without sanitization. An attacker can insert newline characters to forge new log entries, confusing administrators or hiding malicious activity.

**Incorrect (direct logging of user input):**

```java
// VULNERABLE: User input can contain \n or \r
String username = request.getParameter("user");
log.error("Failed login for user: " + username);
// Input: admin\n[INFO] Login successful for user: admin
```

**Correct (sanitized logging):**

```java
// SECURE: Sanitize input by replacing newlines
String username = request.getParameter("user")
                  .replace('\n', '_')
                  .replace('\r', '_');
log.error("Failed login for user: {}", username);

// Better: Use a logging library/layout that handles encoding automatically
// (e.g., Logback's %replace or a JSON layout)
```

**Prevention:**
- Replace `\r` and `\n` characters from all data before logging.
- Use structured logging (JSON) which naturally escapes these characters.
- Limit the length of data written to logs.

**Tools:** SonarQube (S5147), Veracode, Manual Review