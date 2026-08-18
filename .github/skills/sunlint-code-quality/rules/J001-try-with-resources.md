---
title: Always Use try-with-resources for AutoCloseable
impact: HIGH
impactDescription: prevents resource leaks by ensuring streams, connections, and other closeable resources are always closed
tags: resource-management, best-practice, java, error-prone
---

## Always Use try-with-resources for AutoCloseable

Since Java 7, the `try`-with-resources statement automatically closes any resource that implements `AutoCloseable` or `Closeable`. Using manual `finally` blocks to close resources is verbose, error-prone, and can silently swallow exceptions thrown during close.

**Incorrect (manual finally block):**

```java
public void readFile(String path) throws IOException {
    InputStream in = null;
    try {
        in = new FileInputStream(path);
        // process stream
        int b = in.read();
    } catch (IOException e) {
        logger.error("Failed to read file", e);
        throw e;
    } finally {
        if (in != null) {
            try {
                in.close(); // exception here swallows the original
            } catch (IOException ignored) {}
        }
    }
}

public void queryDatabase(Connection conn) throws SQLException {
    Statement stmt = null;
    ResultSet rs = null;
    try {
        stmt = conn.createStatement();
        rs = stmt.executeQuery("SELECT * FROM users");
    } finally {
        if (rs != null) rs.close();   // may throw, hiding original
        if (stmt != null) stmt.close();
    }
}
```

**Correct (try-with-resources):**

```java
public void readFile(String path) throws IOException {
    try (InputStream in = new FileInputStream(path)) {
        // process stream
        int b = in.read();
    }
    // in is automatically closed even if an exception occurs
}

public void queryDatabase(Connection conn) throws SQLException {
    try (Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
        while (rs.next()) {
            // process results
        }
    }
    // both stmt and rs are closed in reverse declaration order
}

// Also works with custom AutoCloseable resources
public void processResource() throws Exception {
    try (MyService service = new MyService()) {
        service.execute();
    }
}
```

**Key benefits:**
- Resources are closed in reverse declaration order, automatically.
- Original exceptions are never swallowed by close failures (suppressed exceptions).
- Cleaner, less boilerplate code.

**Resources that must use try-with-resources:**
- `InputStream` / `OutputStream` / `Reader` / `Writer`
- `Connection` / `Statement` / `ResultSet` (JDBC)
- `HttpClient` / `Socket`
- Any class implementing `AutoCloseable`

**Tools:** PMD (`UseTryWithResources`), SonarQube (`S2093`), IntelliJ Inspections
