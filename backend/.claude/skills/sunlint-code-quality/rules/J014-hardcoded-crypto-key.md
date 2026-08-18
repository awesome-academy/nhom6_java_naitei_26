---
title: Never Hardcode Cryptographic Keys or Initialization Vectors
impact: HIGH
impactDescription: hardcoded crypto keys or IVs give attackers permanent access to all encrypted data — compromised keys cannot be rotated without code changes
tags: security, cryptography, java, secrets
---

## Never Hardcode Cryptographic Keys or Initialization Vectors

Hardcoding cryptographic keys, passwords, secrets, or initialization vectors (IVs) directly in source code is a critical security vulnerability (OWASP A02: Cryptographic Failures). Once the code is in source control or compiled into a binary, the key is permanently exposed. Attackers who access the binary, source code, or logs can decrypt all data protected by that key.

**Incorrect (hardcoded key/IV):**

```java
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

public class EncryptionService {
    // CRITICAL VULNERABILITY: key is hardcoded in source code
    private static final byte[] SECRET_KEY = "MySuperSecretKey".getBytes(); // 16 bytes = AES-128
    private static final byte[] IV = "InitVector123456".getBytes();          // hardcoded IV

    public byte[] encrypt(byte[] data) throws Exception {
        SecretKey key = new SecretKeySpec(SECRET_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(IV);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
        return cipher.doFinal(data);
    }
}

// Also bad: hardcoded passwords for key derivation
public class KeyDerivation {
    private static final String PASSWORD = "hardcoded_password_123"; // exposed in binary
    private static final byte[] SALT = "fixed_salt".getBytes();      // static salt defeats PBKDF2
}
```

**Correct (keys from secure storage):**

```java
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.SecureRandom;

public class EncryptionService {
    private final SecretKey key;

    // Inject key via constructor — loaded from secure storage, not hardcoded
    public EncryptionService(SecretKey key) {
        this.key = key;
    }

    public EncryptedData encrypt(byte[] data) throws Exception {
        // Always generate a random IV per encryption operation
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);  // cryptographically random
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
        byte[] ciphertext = cipher.doFinal(data);

        return new EncryptedData(iv, ciphertext); // store IV alongside ciphertext
    }
}

// Key loading from environment or secrets manager
public class KeyLoader {
    public SecretKey loadKeyFromEnv() {
        String base64Key = System.getenv("AES_SECRET_KEY"); // from environment
        if (base64Key == null) {
            throw new IllegalStateException("AES_SECRET_KEY environment variable not set");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(keyBytes, "AES");
    }

    // Or use Java KeyStore (JKS/PKCS12):
    public SecretKey loadKeyFromKeystore(String keystorePath, char[] storePassword,
                                          String alias, char[] keyPassword) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream fis = new FileInputStream(keystorePath)) {
            ks.load(fis, storePassword);
        }
        return (SecretKey) ks.getKey(alias, keyPassword);
    }
}
```

**Secure key management options:**

| Method | Description |
|--------|-------------|
| Environment variables | Simple, good for containers (`AES_SECRET_KEY`) |
| Java KeyStore (JKS/PKCS12) | Standard Java key storage |
| AWS Secrets Manager / KMS | Cloud-managed, auditable |
| HashiCorp Vault | Enterprise secret management |
| Google Cloud Secret Manager | GCP-native secrets |

**Additional rules:**
- Always use a **random IV** per encryption — never a fixed IV.
- IVs need not be secret but must be unique per encryption.
- Use AES-GCM instead of AES-CBC where possible (provides authentication).
- Key length: AES-128 minimum, AES-256 recommended.

**Tools:** PMD (`HardCodedCryptoKey`, `InsecureCryptoIv`), SonarQube (`S2068`, `S3329`), SpotBugs (`HardCodedKey`)
