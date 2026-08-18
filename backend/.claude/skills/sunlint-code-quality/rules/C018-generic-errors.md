---
title: Do Not Throw Generic Errors
impact: MEDIUM
impactDescription: makes error handling difficult for the caller
tags: error-handling, exceptions, java
---

## Do Not Throw Generic Errors

Throwing `Exception` or `RuntimeException` provides no context to the caller. Always throw specific, meaningful exceptions.

**Incorrect (generic throws):**

```java
public void findUser(String id) throws Exception {
    if (id == null) throw new Exception("ID missing");
}
```

**Correct (specific throws):**

```java
public void findUser(String id) {
    if (id == null) throw new IllegalArgumentException("User ID cannot be null");
}
```

**Tools:** SonarQube (S2221), Manual Review
