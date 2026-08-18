---
title: Do Not Commit Dead Code
impact: MEDIUM
impactDescription: reduces codebase clutter and potential for bugs
tags: readability, clean-code, maintainability, java
---

## Do Not Commit Dead Code

Dead code (commented-out code, unused methods, unreachable branches) increases technical debt and makes the codebase harder to maintain and understand. 

**Incorrect (commented code or unreachable blocks):**

```java
public void calculate() {
    int x = 10;
    // int y = 20; // DEAD CODE
    // if (x > 5) { ... } // DEAD CODE
    System.out.println(x);
}
```

**Correct (clean code):**

```java
public void calculate() {
    int x = 10;
    System.out.println(x);
}
```

**Tools:** IntelliJ Inspections, PMD, SonarQube (S1144), Checkstyle
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
---
title: Do Not Use Error Log Level For Non-Critical Issues
impact: MEDIUM
impactDescription: prevents log noise and ensures relevant alerts
tags: logging, best-practice, java
---

## Do Not Use Error Log Level For Non-Critical Issues

The `ERROR` level should be reserved for issues that require immediate developer attention. For expected failures (like invalid user input), use `WARN` or `INFO`.

**Incorrect (error for everything):**

```java
if (user == null) {
    log.error("User not found: {}", id); // Should be WARN or INFO
}
```

**Correct (appropriate log levels):**

```java
if (user == null) {
    log.warn("Attempt to access non-existent user: {}", id);
}

try {
    db.save(data);
} catch (SQLException e) {
    log.error("Critical database failure", e); // TRUE ERROR
}
```

**Tools:** Manual Review
