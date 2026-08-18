---
title: TLS Clients Must Validate Server Certificates
impact: CRITICAL
impactDescription: prevents Man-in-the-Middle (MitM) attacks by ensuring the server is authentic
tags: tls, certificates, validation, mitm, security, java
---
## TLS Clients Must Validate Server Certificates

Disabling certificate validation (trusting all certificates) makes TLS useless. An attacker can use a self-signed certificate to intercept and read all traffic between your client and the server.

**Incorrect (disabled validation):**

```java
// VULNERABLE: Trusting all certificates
TrustManager[] trustAllCerts = new TrustManager[]{
    new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        public X509Certificate[] getAcceptedIssuers() { return null; }
    }
};
SSLContext sc = SSLContext.getInstance("TLS");
sc.init(null, trustAllCerts, new SecureRandom());
```

**Correct (default/strict validation):**

```java
// SECURE: Use the default JVM TrustManager (uses the system keystore)
SSLContext sc = SSLContext.getDefault();

// SECURE: Use a custom Truststore containing only your CA
KeyStore trustStore = KeyStore.getInstance("PKCS12");
trustStore.load(new FileInputStream("my-ca.p12"), password);

TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
tmf.init(trustStore);

SSLContext sc = SSLContext.getInstance("TLS");
sc.init(null, tmf.getTrustManagers(), new SecureRandom());
```

**Tools:** FindSecBugs (WEAK_TRUST_MANAGER), SonarQube (S4830)