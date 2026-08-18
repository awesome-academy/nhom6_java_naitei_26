---
title: Do Not Ignore Superclass Logic
impact: MEDIUM
impactDescription: prevents bugs where base class functionality is unintentionally disabled
tags: clean-code, inheritance, java
---

## Do Not Ignore Superclass Logic

When overriding a method, you should usually call `super.methodName()` unless you are intentionally and explicitly replacing the base behavior. This ensures that hooks, logging, or state changes in the parent class are preserved.

**Incorrect (ignoring super):**

```java
@Override
protected void onLoginSuccess() {
    // VULNERABLE: Base class might trigger events or metrics!
    myCustomLogic();
}
```

**Correct (calling super):**

```java
@Override
protected void onLoginSuccess() {
    super.onLoginSuccess(); // Preserves base logic
    myCustomLogic();
}
```

**Tools:** IntelliJ Inspections, Manual Review