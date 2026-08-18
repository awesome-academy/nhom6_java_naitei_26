---
title: Utility Classes Must Have a Private Constructor
impact: LOW
impactDescription: utility classes with public or default constructors can be accidentally instantiated, wasting memory and exposing confusing API
tags: design, best-practice, java, clean-code
---

## Utility Classes Must Have a Private Constructor

A **utility class** is a class that consists only of `static` methods and/or constants — it is never meant to be instantiated (e.g., `java.util.Collections`, `java.util.Arrays`, `Math`). Without a private constructor, a utility class:
- Can be instantiated by mistake, creating useless objects.
- May confuse callers about whether an instance has state.
- May be accidentally extended, inheriting a public constructor.

**Incorrect:**

```java
// No constructor — Java adds a public default constructor automatically
public class StringUtils {
    public static String capitalize(String s) { ... }
    public static boolean isBlank(String s) { ... }
}

// Default-visibility constructor — package-accessible
public class MathUtils {
    MathUtils() {} // package-private, should be private
    public static int add(int a, int b) { return a + b; }
}

// Can be instantiated:
StringUtils utils = new StringUtils(); // meaningless object
```

**Correct:**

```java
public final class StringUtils { // final prevents subclassing
    private StringUtils() {
        // Utility class — do not instantiate
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

public final class DateUtils {
    private DateUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static LocalDate parseDate(String date) {
        return LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
```

**Using Lombok `@UtilityClass`:**

```java
import lombok.experimental.UtilityClass;

@UtilityClass // auto-generates private constructor + throws exception + makes class final
public class ValidationUtils {
    public boolean isValidEmail(String email) {
        return email != null && email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    }
}
```

**Conditions for a utility class:**
- All methods are `static`
- No instance fields (or only `static final` constants)
- The class is not designed as a base class

**Tools:** Checkstyle (`HideUtilityClassConstructor`), PMD (`UseUtilityClass`), SonarQube (`S1118`)
