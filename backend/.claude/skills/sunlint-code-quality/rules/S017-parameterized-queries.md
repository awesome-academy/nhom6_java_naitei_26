---
title: Always Use Parameterized Queries
impact: CRITICAL
impactDescription: prevents SQL and NoSQL injection attacks
tags: injection, sql, nosql, database, parameterized, security, java
---

## Always Use Parameterized Queries

SQL injection is one of the most critical security vulnerabilities. Directly concatenating user input into SQL strings allows attackers to manipulate queries, bypass authentication, or steal entire databases.

**Incorrect (string concatenation):**

```java
// VULNERABLE: Direct concatenation
String userId = request.getParameter("id");
String query = "SELECT * FROM users WHERE id = '" + userId + "'";
Statement stmt = connection.createStatement();
ResultSet rs = stmt.executeQuery(query);
```

**Correct (parameterized queries):**

```java
// SECURE: Using PreparedStatement
String userId = request.getParameter("id");
String query = "SELECT * FROM users WHERE id = ?";
PreparedStatement pstmt = connection.prepareStatement(query);
pstmt.setString(1, userId);
ResultSet rs = pstmt.executeQuery();

// Using Spring Data JPA
@Query("SELECT u FROM User u WHERE u.id = :id")
User findUserById(@Param("id") String id);

// Using Hibernate Criteria
List<User> users = session.createSelectionQuery("from User where id = :id", User.class)
    .setParameter("id", userId)
    .getResultList();
```

**Prevention Checklist:**
- Never use `Statement.executeQuery()` with concatenated strings.
- Always use `PreparedStatement` or a secure ORM (Hibernate, Spring Data).
- For NoSQL (e.g., MongoDB), use the driver's query builder instead of string parsing.

**Tools:** SonarQube (S2077, S3649), SpotBugs (FindSecBugs), Checkstyle, OWASP ZAP
