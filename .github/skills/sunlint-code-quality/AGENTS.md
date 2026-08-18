# SunLint Code Quality Standards

> **AI Agent Directive**: Follow these rules when writing/reviewing code. For detailed examples, read rule files in `rules/` directory.

## Skill Structure

```
sunlint-code-quality/
├── SKILL.md           # Full skill documentation with priorities
├── AGENTS.md          # This file - language-agnostic quick reference
└── rules/             # Detailed rule files (language-specific versions installed)
    ├── S017-*.md
    └── C029-*.md
```

## How to Use This Skill

1. **Identify Relevant Rules**: Check the priority categories below to find rules applicable to your current task (e.g., Auth, Security, Error Handling).
2. **Read Rule Details**: Use `view_file` to read the specific `rules/{RULE_ID}-*.md` file for detailed implementation guidelines and code examples in your target language.
3. **Apply Patterns**: Implement the patterns described in the rule file, adapting them to the language you are working in.

---

## Critical Rules (Never Violate)

### Security - Injection Prevention

| Rule | Action |
|------|--------|
| `S017` | Use parameterized queries - no SQL/NoSQL concatenation |
| `S020` | No dynamic code execution (e.g. `eval`, `Function`, `Assembly.Load`) |
| `S025` | Validate ALL client input server-side |
| `S007` | Encode output before interpreter use |

### Security - Authentication

| Rule | Action |
|------|--------|
| `S005` | Server-side authorization at service layer |
| `S006` | No default credentials (admin, root) |
| `S012` | Use secrets management (env vars, vaults) |
| `S026` | TLS 1.2+ mandatory for all connections |
| `S036` | File paths from internal data only |

### Code Quality - Error Handling

| Rule | Action |
|------|--------|
| `C029` | Exception/Catch blocks MUST log error with context |
| `C030` | Use custom error/exception classes |
| `C018` | Do not throw generic errors/exceptions |
| `C035` | Include requestId, entityId in error logs |

### Code Quality - Structure

| Rule | Action |
|------|--------|
| `C014` | Dependency Injection for testability |
| `C017` | No business logic in constructors |
| `C006` | Function/Method names: `Verb-Noun` pattern |
| `C013` | No dead code, unused imports/variables |
| `C041` | No hardcoded secrets in repository |

---

## Rule File Lookup

Detailed rule files are located in the `rules/` directory.

```
rules/{RULE_ID}-{slug}.md
```

**Examples:**
- `rules/S017-parameterized-queries.md`
- `rules/C029-catch-log-root-cause.md`

---

**Version**: 2.3 | **Total Rules**: 65 | **Maintainer**: Sun* Engineering Excellence
