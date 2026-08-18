---
title: Do Not Leave Unused Variables
impact: MEDIUM
impactDescription: reduces clutter and potential for logic errors
tags: readability, clean-code, java
---

## Do Not Leave Unused Variables

Variables that are declared but never used should be removed to keep the code focused and avoid confusion.

**Incorrect (unused variable):**

```java
public void process() {
    int count = 0; // UNUSED
    String name = "Admin";
    System.out.println(name);
}
```

**Correct (clean code):**

```java
public void process() {
    String name = "Admin";
    System.out.println(name);
}
```

**Tools:** IntelliJ Inspections, SonarQube (S1481), SpotBugs