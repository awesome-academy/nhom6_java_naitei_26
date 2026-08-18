---
title: No Hardcoded Secrets In Repository
impact: CRITICAL
impactDescription: prevents secrets from being exposed in version control history
tags: secrets, credentials, git, security, java
---

## No Hardcoded Secrets In Repository

Passwords, API keys, and tokens must never be written directly into the source code. Even if deleted later, they remain in the Git history.

**Incorrect (secrets in code):**

```java
// DANGEROUS: Secret is visible to anyone with code access
public String getS3Client() {
    return "AKIAIOSFODNN7EXAMPLE"; // AWS Key
}
```

**Correct (environment variables or config):**

```java
// SECURE: Value is loaded at runtime
public String getS3Client() {
    return System.getenv("AWS_ACCESS_KEY_ID");
}
```

**Prevention:**
- Use `.gitignore` to exclude config files like `application-local.properties`.
- Use tools like `git-secrets` or `trufflehog` to scan for secrets before committing.

**Tools:** trufflehog, git-secrets, Gitleaks, SonarQube (S2068)
