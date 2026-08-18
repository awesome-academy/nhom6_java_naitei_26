---
title: Logger Must Be private static final
impact: MEDIUM
impactDescription: improper logger declaration can cause memory leaks, serialization issues, and incorrect class attribution in log output
tags: logging, best-practice, java, naming
---

## Logger Must Be private static final

A logger field should always be declared as `private static final` and named after the class it belongs to. This ensures:
- **`private`**: Not accessible from outside the class, encapsulated properly.
- **`static`**: Shared across all instances — not re-created per object, avoiding memory overhead.
- **`final`**: Cannot be reassigned — prevents accidental reassignment.
- **Named with the correct class**: Ensures log entries display the correct source class.

**Incorrect:**

```java
// Not static — a new logger is created for every object instance
public class OrderService {
    private final Logger logger = LoggerFactory.getLogger(OrderService.class); // bad: not static
}

// Not private — accessible from outside
public class UserService {
    public static final Logger LOGGER = LoggerFactory.getLogger(UserService.class); // bad: public
}

// Wrong class name — log entries will show wrong class
public class PaymentService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class); // bad: wrong class
}

// Using java.util.logging without naming it LOG or logger consistently
public class ReportService {
    private static Logger l = Logger.getLogger("reports"); // bad: name "l" is vague; string name is wrong
}
```

**Correct:**

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    public void createOrder(Order order) {
        logger.info("Creating order: orderId={}", order.getId());
    }
}

// For java.util.logging (standard library):
import java.util.logging.Logger;

public class ReportService {
    private static final Logger logger = Logger.getLogger(ReportService.class.getName());
}

// For Log4j 2:
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class NotificationService {
    private static final Logger logger = LogManager.getLogger(NotificationService.class);
}

// With Lombok @Slf4j annotation (auto-generates private static final logger):
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InventoryService {
    public void checkStock(Long productId) {
        log.info("Checking stock: productId={}", productId); // 'log' is auto-generated
    }
}
```

**Standard conventions:**
- Variable name: `logger` (preferred) or `log` (common with Lombok)
- Avoid `LOG`, `LOGGER` (all-caps suggests a compile-time constant, loggers are runtime objects)
- Always pass the owning class literal: `LoggerFactory.getLogger(MyClass.class)`

**Frameworks supported:**
- SLF4J + Logback (recommended)
- Log4j 2
- `java.util.logging` (JUL, standard library)
- Lombok `@Slf4j`, `@Log4j2`, `@CommonsLog`

**Tools:** PMD (`ProperLogger`), SonarQube (`S1312`), IntelliJ Inspections
