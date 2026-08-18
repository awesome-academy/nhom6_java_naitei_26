---
title: Separate Processing And Data Access Layers
impact: HIGH
impactDescription: enforces clean architecture and improves testability
tags: architecture, clean-code, java
---

## Separate Processing And Data Access Layers

Business logic should be decoupled from database operations. Repositories should only handle data retrieval/storage, while Services handle the logic.

**Incorrect (mixed concerns):**

```java
@Service
public class OrderService {
    @Autowired private JdbcTemplate jdbc;
    
    public void checkout(Cart cart) {
        // VULNERABLE: SQL logic directly in Service
        jdbc.update("INSERT INTO orders...");
        // complex business logic here...
    }
}
```

**Correct (layered architecture):**

```java
@Service
public class OrderService {
    @Autowired private OrderRepository repository;
    
    public void checkout(Cart cart) {
        // Business logic...
        Order order = new Order(cart);
        repository.save(order);
    }
}

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
}
```

**Tools:** ArchUnit, Manual Review