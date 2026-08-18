---
title: Limit Upload File Size And Count
impact: MEDIUM
impactDescription: prevents Denial of Service (DoS) attacks via disk exhaustion or memory pressure
tags: uploads, dos, security, java
---

## Limit Upload File Size And Count

Unrestricted file uploads allow an attacker to crash the server by sending massive files or thousands of small files, filling up disk space or consuming all available memory.

**Incorrect (no limits):**

```java
// VULNERABLE: No limit on size
@PostMapping("/upload")
public void handleUpload(@RequestParam("file") MultipartFile file) {
    file.transferTo(new File("/uploads/" + file.getOriginalFilename()));
}
```

**Correct (configured limits):**

```java
// 1. Spring Boot Configuration (application.properties)
// spring.servlet.multipart.max-file-size=2MB
// spring.servlet.multipart.max-request-size=10MB

// 2. Manual check
if (file.getSize() > 2 * 1024 * 1024) {
    throw new BadRequestException("File too large");
}
```

**Tools:** Spring Boot Multipart Properties, OWASP ZAP
