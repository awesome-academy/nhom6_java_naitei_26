---
title: Do Not Use System.out.println in Production Code
impact: MEDIUM
impactDescription: System.out/err output bypasses logging configuration, cannot be disabled without code changes, and may leak sensitive data
tags: logging, best-practice, java, production
---

## Do Not Use System.out.println in Production Code

`System.out.println` and `System.err.println` write directly to standard output/error streams. In production:
- They cannot be filtered, leveled, or structured.
- They cannot be turned off without changing code and redeploying.
- They do not carry context (timestamp, thread, class name) unless added manually.
- Output may be lost in containerized or cloud environments.
- They may print sensitive data (credentials, PII) in server logs.

**Incorrect:**

```java
public class AuthService {
    public User authenticate(String username, String password) {
        System.out.println("Authenticating user: " + username);  // logs PII
        System.out.println("password = " + password);            // SECURITY RISK
        try {
            User user = userRepository.findByUsername(username);
            System.out.println("User found: " + user.getEmail()); // PII leak
            return user;
        } catch (Exception e) {
            System.err.println("Auth failed: " + e.getMessage()); // unstructured
            return null;
        }
    }
}

public class DataProcessor {
    public void process(List<Order> orders) {
        System.out.println("Processing " + orders.size() + " orders"); // no structured log
        for (Order order : orders) {
            System.out.println("Order: " + order); // cannot be filtered
        }
    }
}
```

**Correct (using SLF4J/Logback):**

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    public User authenticate(String username, String password) {
        logger.debug("Authenticating user: username={}", username);
        // Never log the password
        try {
            User user = userRepository.findByUsername(username);
            logger.info("Authentication successful: userId={}", user.getId());
            return user;
        } catch (UserNotFoundException e) {
            logger.warn("Authentication failed: username={}", username);
            throw e;
        }
    }
}

public class DataProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DataProcessor.class);

    public void process(List<Order> orders) {
        logger.info("Starting order processing: count={}", orders.size());
        for (Order order : orders) {
            logger.debug("Processing order: orderId={}", order.getId());
        }
        logger.info("Order processing complete: count={}", orders.size());
    }
}
```

**Exception for tests and scripts:**

`System.out.println` is acceptable only in:
- `main()` methods of CLI tools and standalone scripts
- Unit test setup code (though test frameworks' reporters are better)

Mark these with a `@SuppressWarnings("PMD.SystemPrintln")` annotation if needed.

**Tools:** PMD (`SystemPrintln`), SonarQube (`S106`), Checkstyle
