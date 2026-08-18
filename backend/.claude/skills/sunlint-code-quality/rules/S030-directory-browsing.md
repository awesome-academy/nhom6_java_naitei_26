---
title: Disable Directory Browsing
impact: MEDIUM
impactDescription: prevents attackers from seeing the directory structure and identifying sensitive files
tags: configuration, server, directory-browsing, security, java
---

## Disable Directory Browsing

If directory browsing is enabled, an attacker visiting a folder without an `index.html` file can see all files in that directory. This often leads to the discovery of sensitive configuration files, source code backups, or uploaded data.

**How to Disable:**

**1. In Embedded Tomcat (Spring Boot):**
It is disabled by default. Do not change the `server.tomcat.basedir` to a public-facing path without index files.

**2. In Standard `web.xml` (Legacy):**
```xml
<servlet>
    <servlet-name>default</servlet-name>
    <servlet-class>org.apache.catalina.servlets.DefaultServlet</servlet-class>
    <init-param>
        <param-name>listings</param-name>
        <param-value>false</param-value> <!-- SECURE: Set to false -->
    </init-param>
</servlet>
```

**3. Using Spring Security:**
```java
// Prevent direct access to static resource directories
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/static/**").permitAll()
    .requestMatchers("/config/**").denyAll()
);
```

**Tools:** OWASP ZAP, Manual Review
