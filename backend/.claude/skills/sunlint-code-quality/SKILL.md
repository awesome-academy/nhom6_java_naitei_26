---
name: sunlint-code-quality
description: Code quality and security guidelines from Sun* Engineering Excellence. This skill should be used when writing, reviewing, or refactoring code to ensure security and quality compliance. Triggers on tasks involving authentication, data validation, error handling, or security-sensitive operations.
license: MIT
metadata:
  author: sun-asterisk
  version: "2.4.0"
---

# SunLint Code Quality & Security Standards

Comprehensive code quality and security optimization guide for all projects, maintained by Sun* Engineering Excellence team. Contains **75 rules** across **7 priority categories**, organized by impact to guide automated code review and generation.

## When to Apply

Reference these guidelines when:
- Writing new code in any language (PHP, Python + PySpark, TypeScript, C#, Java, Go, Kotlin, etc.)
- Implementing authentication, authorization, or session management
- Handling user input, file uploads, or external data
- Reviewing code for security vulnerabilities
- Implementing error handling and logging
- Refactoring existing codebase for quality improvements

## Rule Categories by Priority

| Priority | Category | Impact | Rule Count | Prefix |
|----------|----------|--------|------------|--------|
| 1 | Security - Injection Prevention | **CRITICAL** | 10 | `S0xx` |
| 2 | Security - Authentication & Sessions | **CRITICAL** | 12 | `S0xx` |
| 3 | Common - Error Handling & Code Quality | **CRITICAL** | 19 | `C0xx` |
| 4 | Security - Cryptography & TLS | **HIGH** | 8 | `S0xx` |
| 5 | Security - Data Protection | **HIGH** | 10 | `S0xx` |
| 6 | Security - Logging & Monitoring | **MEDIUM** | 6 | `S0xx` |
| 7 | Language Specific (Go/Gin) | **CRITICAL/HIGH** | 10 | `Gxx`/`GNxx` |

---

## Quick Reference

### 1. Security - Injection Prevention (CRITICAL)

These rules prevent the most dangerous vulnerabilities. **Never violate these rules.**

- `S017-parameterized-queries` - Always use parameterized queries, never concatenate SQL
- `S020-eval-code-execution` - Avoid eval() or dynamic code execution
- `S025-server-validation` - Always validate client data server-side
- `S007-output-encoding` - Output encoding before interpreter use
- `S022-context-escaping` - Escape data by output context (HTML, JS, URL)
- `S023-dynamic-js-encoding` - Output encoding for dynamic JS/JSON
- `S019-email-input-sanitization` - Sanitize input before sending emails
- `S055-content-type-validation` - Validate Content-Type in REST services
- `S056-log-injection` - Protect against Log Injection
- `S058-ssrf-protection` - Protect against SSRF attacks

### 2. Security - Authentication & Sessions (CRITICAL)

These rules protect user accounts and session integrity. **Mandatory for all auth flows.**

- `S005-server-authorization` - Enforce authorization at trusted service layer only
- `S006-default-credentials` - Never use default credentials (admin/admin, root/root)
- `S012-secrets-management` - Use secrets management for backend secrets
- `S041-logout-invalidation` - Invalidate session on logout
- `S042-long-lived-sessions` - Re-authenticate for long-lived sessions
- `S044-critical-changes-reauth` - Re-authenticate before critical changes
- `S045-brute-force-protection` - Implement brute-force protection
- `S047-oauth-csrf-protection` - Protect OAuth code flow vs CSRF
- `S048-oauth-redirect-validation` - Validate OAuth redirect URIs exactly
- `S049-auth-code-expiry` - Authentication codes must expire quickly
- `S003-open-redirect` - URL redirects must be in allow list
- `S029-csrf-protection` - Apply CSRF protection

### 3. Common - Error Handling & Code Quality (CRITICAL)

These rules ensure robust error handling and maintainable code. **Mandatory for all code.**

#### Error Handling
- `C029-catch-log-root-cause` - All catch blocks must log root cause with context
- `C030-custom-error-classes` - Use custom error classes, not generic Error/Exception
- `C035-error-context-logging` - Log all relevant context on errors
- `C018-generic-errors` - Do not throw generic errors
- `C019-error-log-level` - Do not use error log level for non-critical issues

#### Code Structure
- `C014-dependency-injection` - Use Dependency Injection for testability
- `C017-no-constructor-logic` - No business logic in constructors
- `C033-separate-data-access` - Separate processing and data access layers
- `C052-controller-parsing` - Separate parsing from controllers
- `C060-superclass-logic` - Do not ignore superclass logic
- `C024-centralize-constants` - Centralize constants in config files
- `C067-no-hardcoded-config` - Do not hardcode configuration values

#### Naming & Style
- `C006-verb-noun-functions` - Function names: verb-noun pattern (getUserById)
- `C013-no-dead-code` - Do not commit dead code
- `C020-no-unused-imports` - Do not import unused modules
- `C022-no-unused-variables` - Do not leave unused variables
- `C023-no-duplicate-names` - No duplicate variable names in scope
- `C042-boolean-naming` - Boolean names: is/has/should prefix
- `C041-no-hardcoded-secrets` - No hardcoded secrets in repo

### 4. Security - Cryptography & TLS (HIGH)

These rules ensure secure data transmission and storage.

- `S009-approved-crypto` - Use only approved crypto algorithms (no MD5, SHA1)
- `S010-csprng` - Use CSPRNG for security purposes (not Math.random())
- `S013-tls-connections` - Always use TLS for all connections
- `S026-tls-encryption` - TLS encryption mandatory for all connections
- `S027-mtls-validation` - Validate mTLS certificates before auth
- `S039-tls-certificate-validation` - TLS clients must validate server certificates
- `S050-token-entropy` - Reference tokens: 128-bit entropy CSPRNG
- `S011-encrypted-client-hello` - Enable Encrypted Client Hello (ECH)

### 5. Security - Data Protection (HIGH)

These rules protect sensitive data from exposure.

- `S004-no-log-credentials` - Do not log credentials/tokens
- `S016-no-sensitive-query-string` - Do not pass sensitive data in query string
- `S036-internal-file-paths` - Use internal data for file paths, strict validation
- `S028-upload-limits` - Limit upload file size and count
- `S030-directory-browsing` - Disable directory browsing
- `S031-secure-cookie-flag` - Set Secure flag on session cookies
- `S032-httponly-cookie` - Set HttpOnly on session cookies
- `S033-samesite-cookie` - Set SameSite on session cookies
- `S034-host-prefix-cookie` - Use __Host- prefix for cookies
- `S035-app-hostnames` - Host apps on different hostnames
- `S037-anti-cache-headers` - Set anti-cache headers for sensitive pages

### 6. Security - Logging & Monitoring (MEDIUM)

These rules ensure proper security monitoring.

- `S051-password-length` - Support 12-64 char passwords
- `S052-otp-entropy` - OTPs must have 20-bit entropy minimum
- `S053-generic-error-messages` - Return generic error messages to users
- `S054-no-default-admin` - Avoid default admin/root accounts
- `S057-synchronized-time` - Use synchronized time (UTC) in logs

---

## How to Use

Read individual rule files for detailed explanations and code examples. Rules are organized by language in the `rules/` directory:

```
rules/python/S017-parameterized-queries.md
rules/typescript/S017-parameterized-queries.md
rules/csharp/S017-parameterized-queries.md
rules/kotlin/S017-parameterized-queries.md
rules/java/S017-parameterized-queries.md
```

Each rule file contains:
- YAML frontmatter with title, impact, and tags
- Brief explanation of why it matters
- Incorrect code example with explanation
- Correct code example with explanation
- Tools for enforcement

## Full Compiled Document

For the complete guide with all rules expanded: `AGENTS.md`

---

## Priority Legend

| Level | Description | Action Required |
|-------|-------------|-----------------|
| **CRITICAL** | Security vulnerabilities OR code quality issues that lead to bugs/maintenance problems | Must fix immediately, block deployment |
| **HIGH** | Security issues that weaken defenses | Fix before production release |
| **MEDIUM** | Quality issues affecting maintainability | Fix when touching related code |

---

**Last Updated**: February 2026 | **Version**: 2.4.1 | **Maintainer**: Sun* Engineering Excellence
