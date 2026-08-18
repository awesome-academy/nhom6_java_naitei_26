---
title: Enable Encrypted Client Hello (ECH)
impact: MEDIUM
impactDescription: protects Server Name Indication (SNI) from eavesdropping
tags: tls, ech, sni, privacy, security, java
---

## Enable Encrypted Client Hello (ECH)

ECH encrypts the Server Name Indication (SNI) in the TLS handshake, preventing network observers from seeing which specific host you are connecting to. This is primarily a privacy feature that prevents ISP/network-level tracking.

**About ECH:**
ECH is managed at the system/infrastructure level (JDK 22+ or via load balancers like Cloudflare/Nginx).

**Correct (ensuring Java client support):**
Java 22 and above have experimental support for ECH. Ensure your runtime environment and HTTP clients are configured to use the latest TLS features.

```java
// For Java 22+:
// -Djdk.tls.client.enableECH=true
// -Djdk.tls.server.enableECH=true
```

**Deployment:**
Enable ECH on your CDN (e.g., Cloudflare) or your entry-point Load Balancer.

**Tools:** Cloudflare, Wireshark (to verify SNI encryption), JDK 22 documentation
