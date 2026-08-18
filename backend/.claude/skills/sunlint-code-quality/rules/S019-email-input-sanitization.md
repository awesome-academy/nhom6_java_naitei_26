---
title: Sanitize Input Before Sending Emails
impact: MEDIUM
impactDescription: prevents email header injection and spam abuse
tags: email, injection, sanitization, security, java
---

## Sanitize Input Before Sending Emails

Email header injection occurs when user data is added to email headers (Subject, To, CC) without sanitizing newline characters. This allows attackers to add extra recipients or change the email content.

**Incorrect (vulnerable email sending):**

```java
// VULNERABLE: Subject can contain \nBcc: victim@example.com
String subject = request.getParameter("subject"); 
SimpleMailMessage message = new SimpleMailMessage();
message.setSubject(subject);
mailSender.send(message);
```

**Correct (sanitization):**

```java
// SECURE: Remove newlines from all header fields
String sanitizedSubject = subject.replaceAll("[\\r\\n]", "");
SimpleMailMessage message = new SimpleMailMessage();
message.setSubject(sanitizedSubject);
mailSender.send(message);
```

**Tools:** SonarQube, Manual Review
