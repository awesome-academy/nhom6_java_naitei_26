---
title: Escape Data By Output Context
impact: MEDIUM
impactDescription: ensures correct encoding for each output context (HTML, JS, URL)
tags: xss, escaping, context, security, java
---

## Escape Data By Output Context

Different contexts require different escaping strategies. Using HTML encoding inside a JavaScript block or an HTML attribute does not fully prevent XSS.

**Incorrect (wrong encoding for context):**

```java
// WRONG: Using forHtml in a JS block
String name = request.getParameter("name");
out.println("<script>var x = '" + Encode.forHtml(name) + "';</script>");
```

**Correct (matching encoder to context):**

```java
// SECURE: Use the context-specific encoder
out.println("<script>var x = '" + Encode.forJavaScript(name) + "';</script>");
out.println("<a href='/profile?u=" + Encode.forUriComponent(name) + "'>View</a>");
```

**Tools:** OWASP Java Encoder
