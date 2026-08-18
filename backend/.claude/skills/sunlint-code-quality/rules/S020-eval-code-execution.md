---
title: Avoid Dynamic Code Execution
impact: CRITICAL
impactDescription: prevents arbitrary code execution vulnerabilities
tags: injection, eval, dynamic-code, rce, security, java
---

## Avoid Dynamic Code Execution

Dynamic execution of code (using `ScriptEngine`, `ClassLoader.defineClass`, or unsecured reflection) allows attackers to execute arbitrary commands if they can control the input, leading to a full system compromise.

**Incorrect (dynamic script execution):**

```java
// DANGEROUS: Running JS strings from user input
ScriptEngineManager manager = new ScriptEngineManager();
ScriptEngine engine = manager.getEngineByName("JavaScript");
String script = request.getParameter("formula"); 
// Attacker: java.lang.Runtime.getRuntime().exec("rm -rf /")
engine.eval(script);
```

**Correct (safe alternatives):**

```java
// 1. Use an expression language with a restricted sandbox (e.g., Spring Expression Language with validation)
StandardEvaluationContext context = new StandardEvaluationContext(data);
// STRICTLY validate or restrict what expressions are allowed

// 2. Use a safe math parser for formulas
Expression e = new ExpressionBuilder(request.getParameter("formula"))
    .build();
double result = e.evaluate();

// 3. Prefer static logic
if ("add".equals(action)) {
    result = a + b;
}
```

**Security Risks:**
- **Remote Code Execution (RCE):** The primary risk of using `eval()` or similar dynamic executors.
- **Resource Exhaustion:** Attackers might run heavy loops or consume memory.

**Tools:** SonarQube (S1523), SpotBugs (FindSecBugs), Manual Review
