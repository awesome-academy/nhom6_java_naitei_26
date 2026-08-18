---
title: Use EnumSet and EnumMap for Enum Keys
impact: MEDIUM
impactDescription: EnumSet and EnumMap use array-backed implementations that are significantly faster and more memory-efficient than HashSet/HashMap for enum keys
tags: performance, best-practice, java, collections
---

## Use EnumSet and EnumMap for Enum Keys

When working with collections whose elements or keys are enum values, `EnumSet` and `EnumMap` should be preferred over `HashSet` and `HashMap`. They use a compact array-backed representation internally — bit vectors for `EnumSet` and an array indexed by ordinal for `EnumMap` — providing better performance and less memory usage.

**Incorrect (using general-purpose collections with enum keys):**

```java
public enum Permission { READ, WRITE, DELETE, ADMIN }

public class User {
    // Using HashSet for enum values — wastes memory, slower
    private Set<Permission> permissions = new HashSet<>();

    public boolean hasPermission(Permission p) {
        return permissions.contains(p); // HashSet lookup — unnecessary overhead
    }
}

public class RolePolicy {
    // Using HashMap for enum keys — suboptimal
    private Map<Permission, String> descriptions = new HashMap<>();

    public void setupDescriptions() {
        descriptions.put(Permission.READ, "Can read resources");
        descriptions.put(Permission.WRITE, "Can write resources");
        descriptions.put(Permission.ADMIN, "Full access");
    }
}
```

**Correct (using EnumSet / EnumMap):**

```java
import java.util.EnumSet;
import java.util.EnumMap;

public enum Permission { READ, WRITE, DELETE, ADMIN }

public class User {
    // EnumSet: compact bit-vector implementation, O(1) operations
    private Set<Permission> permissions = EnumSet.noneOf(Permission.class);

    public void grantPermission(Permission p) {
        permissions.add(p);
    }

    public boolean hasPermission(Permission p) {
        return permissions.contains(p);
    }
}

public class RolePolicy {
    // EnumMap: array-backed, indexed by enum ordinal
    private Map<Permission, String> descriptions = new EnumMap<>(Permission.class);

    public void setupDescriptions() {
        descriptions.put(Permission.READ, "Can read resources");
        descriptions.put(Permission.WRITE, "Can write resources");
        descriptions.put(Permission.ADMIN, "Full access");
    }
}
```

**EnumSet factory methods:**

```java
// Empty set
Set<Permission> none = EnumSet.noneOf(Permission.class);

// All values
Set<Permission> all = EnumSet.allOf(Permission.class);

// Specific values
Set<Permission> readOnly = EnumSet.of(Permission.READ);

// Range (by ordinal order)
Set<Permission> basic = EnumSet.range(Permission.READ, Permission.WRITE);

// Complement
Set<Permission> nonAdmin = EnumSet.complementOf(EnumSet.of(Permission.ADMIN));
```

**Performance comparison:**

| Operation | HashSet | EnumSet |
|-----------|---------|---------|
| `add` | O(1) avg | O(1) bit op |
| `contains` | O(1) avg | O(1) bit op |
| Memory | ~40 bytes/entry | 1 bit/entry |
| Iteration | Hash order | Enum ordinal order |

**When to apply:**
- Use `EnumSet` whenever you have a `Set<SomeEnum>`
- Use `EnumMap` whenever you have a `Map<SomeEnum, V>`
- Both are drop-in replacements that implement `Set` and `Map` respectively

**Tools:** PMD (`UseEnumCollections`), IntelliJ Inspections
