---
title: Use Dependency Injection
impact: HIGH
impactDescription: improves testability and decouples components
tags: dependency-injection, spring, testing, java
---

## Use Dependency Injection

Hardcoding dependencies (using `new`) makes components tightly coupled and difficult to test. Dependency Injection (DI) allows the framework to manage object lifecycles and permits easy mocking during unit tests.

**Incorrect (tight coupling):**

```java
public class UserService {
    private final UserRepository repo = new UserRepository(); // VULNERABLE to tight coupling
    
    public void save(User user) {
        repo.save(user);
    }
}
```

**Correct (constructor injection):**

```java
@Service
public class UserService {
    private final UserRepository repo;

    // SECURE: Dependency is injected via constructor
    public UserService(UserRepository repo) {
        this.repo = repo;
    }
    
    public void save(User user) {
        repo.save(user);
    }
}
```

**Tools:** Spring Framework, Dagger, Guice, SonarQube (S3306)
