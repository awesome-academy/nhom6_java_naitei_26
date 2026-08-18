---
title: Implement Thread-Safe Singleton Correctly
impact: HIGH
impactDescription: a non-thread-safe singleton can create multiple instances under concurrent load, breaking application invariants
tags: concurrency, design-pattern, java, thread-safety
---

## Implement Thread-Safe Singleton Correctly

The Singleton pattern ensures a class has only one instance. In multi-threaded Java applications, a naive singleton implementation can create multiple instances when two threads enter `getInstance()` simultaneously. This is a classic race condition.

**Incorrect (not thread-safe):**

```java
// Lazy initialization — race condition when two threads call getInstance() simultaneously
public class ConfigManager {
    private static ConfigManager instance;

    private ConfigManager() {
        loadConfiguration();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {         // thread A and B both see null
            instance = new ConfigManager(); // both create an instance — BUG
        }
        return instance;
    }
}
```

**Incorrect (broken double-checked locking without volatile):**

```java
public class DatabasePool {
    private static DatabasePool instance; // missing volatile — broken!

    public static DatabasePool getInstance() {
        if (instance == null) {
            synchronized (DatabasePool.class) {
                if (instance == null) {
                    instance = new DatabasePool(); // may be visible as partially initialized
                }
            }
        }
        return instance;
    }
}
```

**Correct (Initialization-on-demand holder idiom — preferred):**

```java
public class ConfigManager {
    private ConfigManager() {
        loadConfiguration();
    }

    // JVM class loading guarantees thread-safe, lazy initialization
    private static final class Holder {
        private static final ConfigManager INSTANCE = new ConfigManager();
    }

    public static ConfigManager getInstance() {
        return Holder.INSTANCE;
    }
}
```

**Correct (double-checked locking with volatile — acceptable):**

```java
public class DatabasePool {
    private static volatile DatabasePool instance; // volatile is required

    private DatabasePool() {}

    public static DatabasePool getInstance() {
        if (instance == null) {
            synchronized (DatabasePool.class) {
                if (instance == null) {
                    instance = new DatabasePool();
                }
            }
        }
        return instance;
    }
}
```

**Correct (enum singleton — simplest and safest):**

```java
// Enum singletons are thread-safe by JVM specification and handle serialization correctly
public enum AppConfig {
    INSTANCE;

    private final String dbUrl;

    AppConfig() {
        dbUrl = System.getenv("DB_URL");
    }

    public String getDbUrl() {
        return dbUrl;
    }
}

// Usage:
AppConfig.INSTANCE.getDbUrl();
```

**Preferred approaches (in order):**
1. **Enum singleton** — safest, handles serialization automatically
2. **Initialization-on-demand holder** — lazy, thread-safe, no synchronization overhead
3. **Double-checked locking with `volatile`** — acceptable for legacy code
4. **Spring `@Component` / `@Service`** — let the DI container manage lifecycle (best for application code)

**Tools:** PMD (`NonThreadSafeSingleton`, `DoubleCheckedLocking`), FindBugs/SpotBugs (`DC_DOUBLECHECK`, `ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD`), SonarQube (`S2168`)
