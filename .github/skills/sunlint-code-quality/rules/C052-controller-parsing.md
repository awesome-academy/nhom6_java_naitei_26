---
title: Separate Parsing From Controllers
impact: HIGH
impactDescription: keeps controllers thin and focuses them on request routing
tags: architecture, controllers, java
---

## Separate Parsing From Controllers

Controllers should handle request mapping and delegation. Heavy parsing, transformation, or mapping logic should be moved to specialized Mapper classes or Services.

**Incorrect (bloated controller):**

```java
@PostMapping("/users")
public String createUser(@RequestBody String rawJson) {
    // VULNERABLE: Parsing logic in Controller
    JSONObject json = new JSONObject(rawJson);
    User user = new User();
    user.setName(json.getString("full_name"));
    // ...
    service.save(user);
    return "OK";
}
```

**Correct (clean controller):**

```java
@PostMapping("/users")
public ResponseEntity<?> createUser(@Valid @RequestBody UserDto dto) {
    // Controller only delegates
    User user = userMapper.toEntity(dto);
    userService.save(user);
    return ResponseEntity.ok().build();
}
```

**Tools:** MapStruct, Jackson (for automatic parsing), Manual Review