---
title: Return Empty Collection Instead of null
impact: MEDIUM
impactDescription: returning null for collections forces every caller to perform a null check, causing NullPointerExceptions when they forget
tags: null-safety, design, java, clean-code
---

## Return Empty Collection Instead of null

Methods that return collections (`List`, `Set`, `Map`, arrays) should **never** return `null` to indicate "no results." Instead, return an empty collection. This eliminates the need for null checks on every call site, reduces potential `NullPointerException` bugs, and makes the API safer and more predictable.

This principle is described in *Effective Java* by Joshua Bloch: *"Never return null in place of an empty array or collection."*

**Incorrect (returning null):**

```java
public List<Order> getOrdersByUser(Long userId) {
    List<Order> orders = orderRepository.findByUserId(userId);
    if (orders.isEmpty()) {
        return null; // forces callers to null-check
    }
    return orders;
}

public String[] getTagsForPost(Long postId) {
    String[] tags = tagRepository.findByPost(postId);
    if (tags == null || tags.length == 0) {
        return null; // callers must check for null before iterating
    }
    return tags;
}

// Callers must remember to null-check — easy to forget:
List<Order> orders = orderService.getOrdersByUser(userId);
for (Order order : orders) { // NPE if orders is null
    processOrder(order);
}
```

**Correct (returning empty collections):**

```java
import java.util.Collections;
import java.util.List;

public List<Order> getOrdersByUser(Long userId) {
    List<Order> orders = orderRepository.findByUserId(userId);
    if (orders == null || orders.isEmpty()) {
        return Collections.emptyList(); // immutable, no allocation overhead
    }
    return orders;
}

// Or with Stream API:
public List<Order> getOrdersByUser(Long userId) {
    return orderRepository.findByUserId(userId) != null
        ? orderRepository.findByUserId(userId)
        : Collections.emptyList();
}

// Using List.of() (Java 9+):
public List<String> getTagsForPost(Long postId) {
    List<String> tags = tagRepository.findByPost(postId);
    return tags != null ? tags : List.of(); // immutable empty list
}

// Arrays:
public String[] getPermissionsForRole(String role) {
    String[] perms = permissionRepo.findByRole(role);
    return perms != null ? perms : new String[0]; // empty array, not null
}

// Callers now iterate safely without null checks:
List<Order> orders = orderService.getOrdersByUser(userId);
for (Order order : orders) { // safe — at worst iterates 0 times
    processOrder(order);
}
```

**Preferred empty collection factories:**

| Type | Factory |
|------|---------|
| `List<T>` | `Collections.emptyList()` or `List.of()` (Java 9+) |
| `Set<T>` | `Collections.emptySet()` or `Set.of()` |
| `Map<K,V>` | `Collections.emptyMap()` or `Map.of()` |
| `T[]` | `new T[0]` |

**Note:** `Collections.emptyList()`, `emptySet()`, and `emptyMap()` return immutable, singleton instances — no memory allocation per call. They are the most efficient choice.

**Exception — when null has semantic meaning:**
If `null` and "empty" are **intentionally different states** (e.g., "not loaded yet" vs "loaded but empty"), `Optional<List<T>>` or a domain wrapper class should be used instead of `null`.

```java
// Distinguish "not configured" from "configured but empty"
public Optional<List<String>> getWhitelist(String service) {
    if (!config.hasWhitelist(service)) return Optional.empty();
    return Optional.of(config.getWhitelist(service));
}
```

**Tools:** PMD (`ReturnEmptyCollectionRatherThanNull`), SonarQube (`S1168`), IntelliJ Inspections
