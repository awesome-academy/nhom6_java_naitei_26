---
title: Use java.time Instead of Legacy Date and Calendar
impact: MEDIUM
impactDescription: java.util.Date and Calendar are mutable, thread-unsafe, and poorly designed; java.time provides a modern, immutable, thread-safe API
tags: best-practice, java, modernization, thread-safety
---

## Use java.time Instead of Legacy Date and Calendar

`java.util.Date` and `java.util.Calendar` were introduced in Java 1.0 and 1.1 respectively. They are mutable (not thread-safe), have confusing APIs (months are 0-indexed in `Calendar`), and are generally considered a design failure. Since Java 8, the `java.time` package (JSR-310) provides a comprehensive, immutable, thread-safe date/time API.

**Incorrect (using legacy Date/Calendar):**

```java
import java.util.Date;
import java.util.Calendar;

public class OrderService {
    public Date calculateDeliveryDate(Date orderDate, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(orderDate);
        cal.add(Calendar.DAY_OF_MONTH, days); // Calendar.DAY_OF_MONTH is error-prone
        return cal.getTime(); // mutable — caller can modify the returned value
    }

    public boolean isExpired(Date expiryDate) {
        return new Date().after(expiryDate); // new Date() for "now" is verbose
    }

    // Storing timestamps
    private Date createdAt = new Date(); // mutable — bad for records
}
```

**Correct (using java.time):**

```java
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.time.Duration;

public class OrderService {
    public LocalDate calculateDeliveryDate(LocalDate orderDate, int days) {
        return orderDate.plusDays(days); // fluent, immutable, clear
    }

    public boolean isExpired(LocalDateTime expiryDateTime) {
        return LocalDateTime.now().isAfter(expiryDateTime);
    }

    // Storing timestamps
    private final Instant createdAt = Instant.now(); // immutable

    // Working with timezones
    public ZonedDateTime getOrderTimeInJST(Instant instant) {
        return instant.atZone(ZoneId.of("Asia/Tokyo"));
    }

    // Duration/period calculation
    public long getDaysUntilDeadline(LocalDate deadline) {
        return Duration.between(
            LocalDate.now().atStartOfDay(),
            deadline.atStartOfDay()
        ).toDays();
    }
}
```

**Migration guide:**

| Legacy | java.time replacement |
|--------|----------------------|
| `java.util.Date` | `java.time.Instant` (for UTC timestamps) |
| `java.util.Calendar` | `java.time.ZonedDateTime` |
| `java.sql.Date` | `java.time.LocalDate` |
| `java.sql.Timestamp` | `java.time.LocalDateTime` or `Instant` |
| `SimpleDateFormat` | `java.time.format.DateTimeFormatter` |

**Key types in java.time:**
- `LocalDate` — date without time/timezone (e.g., 2024-01-15)
- `LocalTime` — time without date/timezone
- `LocalDateTime` — date + time, no timezone
- `ZonedDateTime` — date + time + timezone
- `Instant` — a Unix timestamp (UTC)
- `Duration` — time-based amount (seconds, nanoseconds)
- `Period` — date-based amount (years, months, days)

**Tools:** PMD (`ReplaceJavaUtilDate`, `ReplaceJavaUtilCalendar`), SonarQube (`S2143`)
