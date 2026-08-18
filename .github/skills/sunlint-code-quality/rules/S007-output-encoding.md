---
title: Output Encoding Before Interpreter Use
impact: CRITICAL
impactDescription: prevents Cross-Site Scripting (XSS) and other injection attacks
tags: encoding, xss, output, security, java
---

## Output Encoding Before Interpreter Use

When displaying user-provided data in HTML, a URL, or a script block, you must encode the data to prevent the browser from interpreting it as code. This is the primary defense against Cross-Site Scripting (XSS).

**Incorrect (raw output):**

```java
// VULNERABLE: Direct print of user input to HTML
String name = request.getParameter("name");
out.println("<div>Welcome, " + name + "</div>");
// Input: <script>alert('xss')</script>
```

**Correct (output encoding):**

```java
import org.owasp.encoder.Encode;

// 1. HTML Body Context
String name = request.getParameter("name");
String safeHtml = Encode.forHtml(name);
out.println("<div>Welcome, " + safeHtml + "</div>");

// 2. HTML Attribute Context
out.println("<input type='text' value='" + Encode.forHtmlAttribute(value) + "'>");

// 3. JavaScript Context
out.println("<script>var userName = '" + Encode.forJavaScript(name) + "';</script>");

// 4. URL Context
String safeUrl = "https://example.com/search?q=" + URLEncoder.encode(query, "UTF-8");
```

**Using Templating Engines (Recommended):**
Modern engines like **Thymeleaf**, **JSP** (with JSTL), and **Freemarker** perform auto-encoding by default.

```html
<!-- Thymeleaf: Automatically encodes 'name' -->
<div th:text="${user.name}"></div>
```

**Tools:** OWASP Java Encoder, SonarQube (S2253), Snyk, Manual Review
