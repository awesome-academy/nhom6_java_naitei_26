---
title: Validate mTLS Certificates Before Auth
impact: HIGH
impactDescription: ensures that only clients with valid, trusted certificates can access the service
tags: tls, mtls, authentication, security, java
---

## Validate mTLS Certificates Before Auth

In a mutual TLS (mTLS) setup, the server must verify the client's certificate before allowing the request to proceed. This provides strong, certificate-based authentication.

**Implementation (Spring Security):**

```java
// http.x509(x509 -> x509
//     .subjectPrincipalRegex("CN=(.*?)(?:,|$)")
//     .userDetailsService(myUserDetailsService)
// );
```

**Key Points:**
- Ensure the Truststore only contains the CAs you explicitly trust.
- Verify expiration and revocation status of the client certificate.
- Link the Certificate's Common Name (CN) or Subject Alternative Name (SAN) to a specific user/service identity.

**Tools:** OpenSSL, Spring Security X.509
