---
title: Function Names Verb-Noun Pattern
impact: MEDIUM
impactDescription: improves code readability and maintainability
tags: readability, naming, best-practice, java
---

## Function Names Verb-Noun Pattern

Method names should clearly state what they do. Following a `verbNoun` pattern (camelCase) makes the code more intuitive and easier to scan.

**Incorrect (vague or reverse naming):**

```java
public void userData(User user) { ... }
public List<User> usersGet() { ... }
public void process() { ... }
```

**Correct (verb-noun naming):**

```java
public void saveUser(User user) { ... }
public List<User> getAllUsers() { ... }
public void processPayment() { ... }
public boolean isValidEmail(String email) { ... }
```

**Common Verbs:**
- `get` / `find`: Retrieve data.
- `save` / `create` / `update`: Modify data.
- `is` / `has`: Boolean checks.
- `validate`: Check constraints.
- `calculate`: Perform computations.

**Tools:** Checkstyle, IntelliJ Inspections, Manual Review
