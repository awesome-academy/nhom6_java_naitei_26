---
title: Override equals() and hashCode() Together
impact: HIGH
impactDescription: violating the equals-hashCode contract breaks HashMap, HashSet, and other hash-based collections silently
tags: correctness, contract, java, error-prone
---

## Override equals() and hashCode() Together

In Java, `equals()` and `hashCode()` share a contract: objects that are equal (via `equals()`) **must** have the same hash code. If you override one without overriding the other, hash-based collections (`HashMap`, `HashSet`, `Hashtable`) will malfunction — objects may become unreachable or duplicates may appear.

**Incorrect (only overrides equals):**

```java
public class User {
    private Long id;
    private String email;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }
    // Missing hashCode! — HashSet will treat two equal Users as different objects
}

// Result: map.put(user1); map.get(user2) returns null even if user1.equals(user2)
Set<User> set = new HashSet<>();
set.add(new User(1L, "a@b.com"));
set.contains(new User(1L, "a@b.com")); // false! Bug!
```

**Incorrect (only overrides hashCode):**

```java
public class Order {
    private Long id;

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    // Missing equals! — equals uses identity (==) by default
}
```

**Correct (both overridden consistently):**

```java
public class User {
    private Long id;
    private String email;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); // same fields as equals
    }
}

// Using records (Java 16+): equals and hashCode are auto-generated
public record User(Long id, String email) {}

// Using Lombok:
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {
    @EqualsAndHashCode.Include
    private Long id;
    private String email;
}
```

**Rules to follow:**
- Use the **same fields** in both `equals()` and `hashCode()`.
- Prefer `Objects.equals()` and `Objects.hash()` over manual null checks.
- Consider using Lombok `@EqualsAndHashCode` or Java Records for value objects.
- Never include mutable fields in `hashCode()` if the object will be stored in a hash collection.

**Tools:** PMD (`OverrideBothEqualsAndHashcode`), Checkstyle (`EqualsHashCode`), IntelliJ Inspections
