---
title: Use equals() for String and Object Comparison, Not ==
impact: HIGH
impactDescription: using == compares references, not values, causing silent bugs that only appear at runtime
tags: correctness, java, error-prone, string
---

## Use equals() for String and Object Comparison, Not ==

In Java, `==` checks **reference equality** (are these the same object in memory?). For Strings and other objects, you almost always want **value equality** — use `.equals()`. While string literals may be interned (sharing references), strings from variables, method returns, or `new String(...)` will have different references, making `==` unreliable.

**Incorrect (using == for string comparison):**

```java
String status = getStatusFromDatabase(); // returns "ACTIVE"
if (status == "ACTIVE") {               // BUG: may be false even when value is "ACTIVE"
    activateUser();
}

String s1 = new String("hello");
String s2 = new String("hello");
if (s1 == s2) {  // always false — different objects
    // never executed
}

public boolean isAdmin(String role) {
    return role == "ADMIN"; // unreliable
}
```

**Correct (using equals for string comparison):**

```java
String status = getStatusFromDatabase();
if ("ACTIVE".equals(status)) {  // null-safe: won't NPE if status is null
    activateUser();
}

// Or with Objects.equals for null-safety on both sides:
if (Objects.equals(status, "ACTIVE")) {
    activateUser();
}

public boolean isAdmin(String role) {
    return "ADMIN".equals(role);    // preferred: literal first to avoid NPE
    // or: return role != null && role.equals("ADMIN");
}

// For enums: use == (enums are singletons, == is correct and preferred)
if (status == Status.ACTIVE) {  // correct for enums
    activateUser();
}
```

**Literal-first pattern:**

Placing the literal on the left side of `equals()` is a common defensive pattern — if the variable is `null`, the call returns `false` instead of throwing `NullPointerException`:

```java
// Risky: may throw NPE if name is null
if (name.equals("John")) { ... }

// Safe: returns false if name is null
if ("John".equals(name)) { ... }
```

**Key distinctions:**
- `String` and all objects: use `.equals()` or `Objects.equals()`
- `enum` types: use `==` (enums are singleton instances)
- Primitives (`int`, `boolean`, etc.): use `==`

**Tools:** PMD (`CompareObjectsWithEquals`, `UseEqualsToCompareStrings`), FindBugs/SpotBugs (`ES_COMPARING_STRINGS_WITH_EQ`), IntelliJ Inspections
