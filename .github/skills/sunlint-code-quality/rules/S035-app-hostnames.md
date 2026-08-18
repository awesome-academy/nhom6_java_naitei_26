---
title: Host Apps On Different Hostnames
impact: MEDIUM
impactDescription: prevents Cross-Site Scripting (XSS) from spreading between different applications on the same domain
tags: architecture, isolation, security, java
---
## Host Apps On Different Hostnames

If multiple applications (e.g., `app.example.com` and `admin.example.com`) are hosted on the same domain (`example.com`) and share cookies or have permissive CORS, an XSS in one app can be used to attack the other.

**Best Practice:**
Use distinct subdomains or entirely different domains for applications with different trust levels.

**Correct (Isolation):**
- **Public Website:** `www.sun-asterisk.vn`
- **Customer Portal:** `portal.sun-asterisk.vn`
- **Internal Admin:** `admin-internal.sun-asterisk.vn` (or on a private VPC)

**Why it matters:**
- **Cookie Isolation:** Browsers can be configured to share cookies across subdomains.
- **Same-Origin Policy:** Distinct origins provide a strong security boundary.

**Tools:** Architecture Review