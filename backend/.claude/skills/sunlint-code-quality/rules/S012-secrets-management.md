---
title: Use Secrets Management For Backend Secrets
impact: CRITICAL
impactDescription: prevents exposure of sensitive credentials in source code and version control
tags: secrets, management, vault, security, java
---
## Use Secrets Management For Backend Secrets

Sensitive data like API keys, database passwords, and private certificates should never be stored in plaintext in the codebase or checked into version control. Use a dedicated secrets management tool.

**Incorrect (secrets in source code):**

```java
// VULNERABLE: Hardcoded API Key
public static final String STRIPE_SECRET = "sk_test_REPLACE_WITH_YOUR_ACTUAL_KEY";
```

**Correct (external secrets management):**

```java
// 1. Environment Variables (Simple)
String apiKey = System.getenv("STRIPE_SECRET_KEY");

// 2. Spring Cloud Vault / Config (Recommended for Production)
@Value("${my.secret.key}")
private String secretKey;

// 3. AWS Secrets Manager / Azure Key Vault SDK
GetSecretValueRequest request = new GetSecretValueRequest().withSecretId("stripe/live/key");
String secret = client.getSecretValue(request).getSecretString();
```

**Tools:** HashiCorp Vault, AWS Secrets Manager, Google Cloud Secret Manager, Kubernetes Secrets
