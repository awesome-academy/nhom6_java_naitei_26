---
title: Do Not Pass Sensitive Data In Query String
impact: HIGH
impactDescription: prevents sensitive data from leaking into browser history, server logs, and referrer headers
tags: query-string, sensitive-data, transport, security, java
---

## Do Not Pass Sensitive Data In Query String

URL parameters (the query string) are visible in browser history, bookmarks, proxy logs, and `Referer` headers. Sensitive data like passwords, tokens, or personal identifiers should never be part of a URL.

**Incorrect (sensitive query strings):**

```java
// VULNERABLE: Token is in the URL
GET /api/user-details?auth_token=eyJhbGciOiJIUzI1NiI...
```

**Correct (headers or body):**

```java
// SECURE: Token passed in Authorization header
GET /api/user-details
Authorization: Bearer eyJhbGciOiJIUzI1NiI...

// SECURE: Data passed in POST body
POST /api/reset-password
Content-Type: application/json
{ "token": "...", "newPassword": "..." }
```

**Always use:**
- `POST` / `PUT` for any request containing sensitive data.
- Standard headers like `Authorization` for tokens.

**Tools:** OWASP ZAP, Manual Audit
