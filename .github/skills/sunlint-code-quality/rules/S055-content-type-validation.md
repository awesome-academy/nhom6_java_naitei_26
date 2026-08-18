---
title: Validate Content-Type In REST Services
impact: MEDIUM
impactDescription: prevents content-type confusion attacks and parsing vulnerabilities
tags: rest, content-type, validation, api, security, java
---
## Validate Content-Type In REST Services

Accepting unexpected content types can lead to parsing vulnerabilities (like XML External Entity injection if XML is accidentally processed) or bypass security controls that only inspect certain media types.

**Incorrect (accepting any content):**

```java
// VULNERABLE: No restriction on Content-Type
@PostMapping("/api/data")
public void handleData(@RequestBody String data) {
    // Parser might try to be "smart" and parse XML inside a String
}
```

**Correct (explicit Media Type):**

```java
// SECURE: Only accept JSON
@PostMapping(value = "/api/data", consumes = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> handleData(@RequestBody MyDto dto) {
    return ResponseEntity.ok().build();
}
```

**Implementation Details:**
- Use the `consumes` attribute in `@RequestMapping` / `@PostMapping`.
- Ensure the server returns `415 Unsupported Media Type` for invalid requests.
- Reject `multipart/form-data` unless specifically required for file uploads.

**Tools:** OWASP ZAP, Postman (testing 415), Manual Review