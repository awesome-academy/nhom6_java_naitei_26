---
title: Use CSPRNG For Security Purposes
impact: HIGH
impactDescription: prevents predictable random values that attackers can guess
tags: randomness, csprng, security, java
---

## Use CSPRNG For Security Purposes

Standard random number generators (like `java.util.Random`) are predictable and should never be used for security-sensitive operations like generating passwords, session tokens, or initialization vectors (IVs).

**Incorrect (predictable random):**

```java
// DANGEROUS: Uses a linear congruential generator (LCG)
Random rand = new Random();
int token = rand.nextInt(1000000);
```

**Correct (cryptographically secure random):**

```java
// SECURE: Uses SecureRandom (CSPRNG)
SecureRandom secureRand = new SecureRandom();
byte[] tokenBytes = new byte[32];
secureRand.nextBytes(tokenBytes);
String token = Base64.getEncoder().encodeToString(tokenBytes);
```

**When to use CSPRNG:**
- Session IDs and CSRF tokens.
- Password reset tokens.
- Cryptographic salts and IVs.
- Temporary passwords/OTPs.

**Tools:** SonarQube (S2245), FindSecBugs
