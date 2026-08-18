---
title: Output Encoding For Dynamic JS/JSON
impact: HIGH
impactDescription: prevents code injection in JavaScript contexts
tags: xss, javascript, json, encoding, security, java
---

## Output Encoding For Dynamic JS/JSON

When embedding user data into a JSON object that will be rendered inside a `<script>` tag, you must ensure that characters like `<` and `>` are escaped to prevent an attacker from closing the script tag and opening a new one.

**Incorrect (direct embedding):**

```java
// VULNERABLE: Input </script><script>alert('xss')</script>
String jsonData = mapper.writeValueAsString(userData);
out.println("<script>var data = " + jsonData + ";</script>");
```

**Correct (properly escaped JSON):**

```java
// SECURE: Use Jackson features or OWASP Encoder for JS
// Jackson can be configured to escape non-ascii characters
out.println("<script>var data = " + Encode.forJavaScript(jsonData) + ";</script>");
```

**Tools:** Jackson `JsonGenerator.Feature.ESCAPE_NON_ASCII`, OWASP Java Encoder
