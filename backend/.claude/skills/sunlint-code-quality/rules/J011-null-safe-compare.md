---
title: Put Literals First in String Comparisons to Avoid NullPointerException
impact: MEDIUM
impactDescription: variable.equals("literal") throws NPE if variable is null; reversing operands makes comparisons null-safe without extra null checks
tags: null-safety, best-practice, java, error-prone
---

## Put Literals First in String Comparisons to Avoid NullPointerException

When comparing a string variable to a known literal value, placing the literal on the **left** side of `.equals()` prevents a `NullPointerException` if the variable is `null`. Since a string literal is never `null`, calling `.equals()` on it is always safe.

**Incorrect (variable first — may throw NPE):**

```java
public boolean isActive(String status) {
    return status.equals("ACTIVE"); // NPE if status is null
}

public void handleRequest(HttpServletRequest request) {
    String method = request.getMethod();
    if (method.equals("POST")) {    // NPE if method is null (unlikely but possible)
        processPost();
    }
}

public boolean checkRole(User user) {
    String role = user.getRole();       // getRole() might return null
    return role.equals("ADMIN");        // NPE
}
```

**Correct (literal first — null-safe):**

```java
public boolean isActive(String status) {
    return "ACTIVE".equals(status); // returns false if status is null — no NPE
}

public void handleRequest(HttpServletRequest request) {
    String method = request.getMethod();
    if ("POST".equals(method)) {    // safe even if method is null
        processPost();
    }
}

public boolean checkRole(User user) {
    String role = user.getRole();
    return "ADMIN".equals(role);    // null-safe
}

// Also applies to equalsIgnoreCase:
public boolean isAdminIgnoreCase(String role) {
    return "admin".equalsIgnoreCase(role); // null-safe
}

// Constant comparison:
public static final String DEFAULT_LANG = "en";

public boolean isDefaultLanguage(String lang) {
    return DEFAULT_LANG.equals(lang); // constant on left — same principle
}
```

**When to use Objects.equals() instead:**

Use `Objects.equals(a, b)` when **both** values may be `null` and you want a symmetric comparison:

```java
// Objects.equals handles both nulls:
boolean same = Objects.equals(user.getRole(), adminRole); // null-safe on both sides

// Equivalent to:
// user.getRole() == adminRole || (user.getRole() != null && user.getRole().equals(adminRole))
```

**compareTo / compareToIgnoreCase:**

Note that reversing the literal changes the sign of the result:

```java
// Original:  x.compareTo("bar") > 0
// Reversed: "bar".compareTo(x) < 0  ← sign flipped!

// Be careful when converting compareTo usage
if ("bar".compareTo(x) < 0) { ... } // equivalent to x.compareTo("bar") > 0
```

**Tools:** PMD (`LiteralsFirstInComparisons`), SonarQube (`S1132`), IntelliJ Inspections
