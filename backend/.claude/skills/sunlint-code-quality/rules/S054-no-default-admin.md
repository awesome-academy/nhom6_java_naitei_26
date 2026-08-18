---
title: Avoid Default Admin/Root Accounts
impact: MEDIUM
impactDescription: prevents access via widely known default credentials
tags: authentication, security, best-practice, java
---
## Avoid Default Admin/Root Accounts

Systems should not ship with default, hardcoded administrator accounts.

**Correct:**
- Force password change on first login.
- Generate a unique random password during the installation/setup process.
- Do not use "admin" or "root" as default usernames.

**Tools:** Security Audit