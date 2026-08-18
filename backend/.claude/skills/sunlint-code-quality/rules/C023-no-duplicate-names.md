---
title: No Duplicate Variable Names In Scope
impact: MEDIUM
impactDescription: prevents variable shadowing and unintentional logic errors
tags: clean-code, maintainability, java
---

## No Duplicate Variable Names In Scope

Using the same name for a local variable as a member variable (shadowing) makes the code hard to read and can lead to bugs where the wrong variable is updated.

**Incorrect (shadowing):**

```java
public class UserService {
    private String name;

    public void updateName(String name) {
        // VULNERABLE: Which 'name' is being used?
        name = name; // Bug: logic error
    }
}
```

**Correct (clear naming):**

```java
public class UserService {
    private String name;

    public void updateName(String newName) {
        this.name = newName;
    }
}
```

**Tools:** IntelliJ Inspections, Checkstyle (HiddenField), SonarQube (S1117)