---
title: No Business Logic In Constructors
impact: MEDIUM
impactDescription: prevents side effects during object instantiation and improves testability
tags: clean-code, best-practice, java
---

## No Business Logic In Constructors

Constructors should only be used for initialized fields. Performing business logic (database calls, network requests) inside a constructor makes the object hard to test and can lead to unexpected side effects during initialization.

**Incorrect (logic in constructor):**

```java
public class OrderService {
    public OrderService() {
        // VULNERABLE: Side effects during new OrderService()
        loadConfiguration();
        connectToDatabase();
    }
}
```

**Correct (separate initialization):**

```java
public class OrderService {
    public OrderService() {
        // Only simple field assignments
    }

    @PostConstruct
    public void init() {
        // Business logic or heavy setup here
    }
}
```

**Tools:** Manual Review, SonarQube (S1699)
