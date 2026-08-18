---
title: Use Only Approved Crypto Algorithms
impact: CRITICAL
impactDescription: prevents the use of broken or weak cryptography that can be easily cracked
tags: cryptography, encryption, algorithms, security, java
---

## Use Only Approved Crypto Algorithms

Avoid using deprecated or weak cryptographic algorithms (like MD5, SHA1, DES, or Blowfish with small keys). These are technically broken and can be cracked in minutes by modern hardware.

**Incorrect (weak crypto):**

```java
// VULNERABLE: MD5 is broken
MessageDigest md = MessageDigest.getInstance("MD5");

// VULNERABLE: DES is weak
Cipher c = Cipher.getInstance("DES");
```

**Correct (approved crypto):**

```java
// SECURE: SHA-256 or SHA-512 for hashing
MessageDigest md = MessageDigest.getInstance("SHA-256");

// SECURE: AES-256 for symmetric encryption
Cipher c = Cipher.getInstance("AES/GCM/NoPadding");

// SECURE: Argon2 or BCrypt for password hashing
String hash = BCrypt.hashpw(password, BCrypt.gensalt());
```

**Recommended Algorithms:**
- **Hashing:** SHA-256, SHA-512, SHA-3.
- **Encryption:** AES (128-bit or 256-bit) with GCM mode.
- **Passwords:** Argon2, BCrypt, SCrypt.

**Tools:** SonarQube (S1311), FindSecBugs
