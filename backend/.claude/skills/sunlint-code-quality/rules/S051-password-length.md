---
title: Support 12-64 Char Passwords
impact: MEDIUM
impactDescription: follows modern security standards for password length
tags: password, policy, security, java
---
## Support 12-64 Char Passwords

Restricting password length too much prevents strong passphrases. Allowing too much length without limits can lead to hashing-based DoS.

**Correct (Hibernate Validator):**

```java
public class UserDto {
    @Size(min = 12, max = 64)
    private String password;
}
```

**Tools:** NIST Guidelines, Hibernate Validator