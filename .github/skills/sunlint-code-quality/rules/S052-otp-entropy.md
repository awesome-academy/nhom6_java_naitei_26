---
title: OTPs Must Have 20-bit Entropy
impact: MEDIUM
impactDescription: ensures that numeric OTPs are difficult to guess within their short lifespan
tags: otp, authentication, entropy, security, java
---
## OTPs Must Have 20-bit Entropy

Numeric One-Time Passwords (OTPs) must be long enough to prevent guessing. A 6-digit OTP has approximately 20 bits of entropy, which is the recommended minimum for a short-lived token.

**Correct (6-digit OTP):**

```java
SecureRandom random = new SecureRandom();
int otp = 100000 + random.nextInt(900000); // 6 digits
```

**Strategy:**
- Length: Minimum **6 digits**.
- Expiry: **1-5 minutes**.
- Rate limit attempts: Max **3-5 attempts** per OTP.

**Tools:** Google Authenticator, Twilio Authy