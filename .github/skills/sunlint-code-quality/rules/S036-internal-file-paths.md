---
title: Use Internal Data For File Paths
impact: CRITICAL
impactDescription: prevents Path Traversal attacks (LFI/RFI)
tags: architecture, path-traversal, security, java
---
## Use Internal Data For File Paths

Never allow a user to specify the file name or path directly in a request (e.g., `?file=report.pdf`). Attackers can use `../` to access sensitive files like `/etc/passwd`.

**Incorrect (trusting user for file path):**

```java
// VULNERABLE: Attacker input: ../../../etc/passwd
@GetMapping("/download")
public void download(@RequestParam String file, HttpServletResponse response) {
    File target = new File("/var/www/uploads/" + file);
    // ...
}
```

**Correct (internal mapping):**

```java
// SECURE: Use a database ID and look up the internal path
@GetMapping("/download/{id}")
public void download(@PathVariable Long id, HttpServletResponse response) {
    FileInfo info = fileRepo.findById(id).orElseThrow();
    File target = new File("/var/www/uploads/" + info.getInternalStorageName());
    // ...
}

// SECURE: Strict validation (Whitelist/Regex)
if (!file.matches("^[a-zA-Z0-0._-]+\\.pdf$")) {
    throw new SecurityException("Invalid filename");
}
```

**Tools:** SonarQube (S2083), OWASP ZAP, Manual Review