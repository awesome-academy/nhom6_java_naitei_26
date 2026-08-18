---
title: Centralize Constants In Config Files
impact: MEDIUM
impactDescription: improves maintainability by avoiding "magic strings" and "magic numbers"
tags: refactoring, clean-code, java
---

## Centralize Constants In Config Files

Literals (strings, numbers) used multiple times should be defined as constants in a centralized place rather than hardcoded throughout the logic.

**Incorrect (hardcoded literals):**

```java
public void process() {
    if ("ADMIN".equals(user.getRole())) { // Magic string
        // ...
    }
}
```

**Correct (centralized constants):**

```java
public class AuthConstants {
    public static final String ROLE_ADMIN = "ADMIN";
}

public void process() {
    if (AuthConstants.ROLE_ADMIN.equals(user.getRole())) {
        // ...
    }
}
```

**Tools:** IntelliJ "Extract Constant", Checkstyle (MagicNumber), SonarQube (S1192)