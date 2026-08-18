---
title: Boolean Names Prefix
impact: MEDIUM
impactDescription: improves code readability by making boolean variables sound like questions
tags: naming, readability, java
---

## Boolean Names Prefix

Boolean variables and methods should be prefixed with `is`, `has`, `should`, `can`, or `exists` to be instantly recognizable as true/false values.

**Incorrect (missing prefix):**

```java
boolean valid = true;
boolean active(User user) { ... }
```

**Correct (standard boolean naming):**

```java
boolean isValid = true;
boolean hasPermission = false;
boolean isActive(User user) { ... }
```

**Tools:** IntelliJ Inspections, Checkstyle, Manual Review