---
title: Reference Tokens Entropy
impact: HIGH
impactDescription: ensures that tokens cannot be guessed by an attacker
tags: tokens, entropy, csprng, security, java
---
## Reference Tokens Entropy

Opaque reference tokens (like session IDs or API keys) must be generated using a CSPRNG and have enough entropy to prevent guessing (at least 128 bits).

**Correct (SecureRandom):**

```java
SecureRandom random = new SecureRandom();
byte[] bytes = new byte[24]; // 192 bits of entropy
random.nextBytes(bytes);
String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
```

**Tools:** SecureRandom, SonarQube (S2245)