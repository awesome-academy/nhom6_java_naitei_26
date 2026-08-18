---
title: Do Not Import Unused Modules
impact: MEDIUM
impactDescription: reduces compilation time and avoids namespace pollution
tags: readability, clean-code, java
---

## Do Not Import Unused Modules

Unused imports clutter the code and can lead to confusion if multiple classes have the same name in different packages.

**Incorrect (unused imports):**

```java
import java.util.List;
import java.util.ArrayList; // UNUSED
import java.util.stream.Collectors; // UNUSED

public class MyClass {
    public void run(List<String> list) { ... }
}
```

**Correct (clean imports):**

```java
import java.util.List;

public class MyClass {
    public void run(List<String> list) { ... }
}
```

**Tools:** IntelliJ "Optimize Imports", Checkstyle (UnusedImports), PMD