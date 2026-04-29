# Skill Registry — arch-validator

Generated: 2026-04-23

## Project Conventions

| File | Purpose |
|------|---------|
| AGENTS.md | Coding guidelines: architecture rules, naming, testing, DI patterns |

## Available Skills

| Skill | Trigger |
|-------|---------|
| sdd-init | Initialize SDD context |
| sdd-explore | Explore/investigate ideas before a change |
| sdd-propose | Create a change proposal |
| sdd-spec | Write specifications with scenarios |
| sdd-design | Create technical design document |
| sdd-tasks | Break down change into task checklist |
| sdd-apply | Implement tasks from the change |
| sdd-verify | Validate implementation matches specs |
| sdd-archive | Sync delta specs and archive a change |
| branch-pr | Create a pull request |
| issue-creation | Create a GitHub issue |
| judgment-day | Adversarial dual review protocol |
| skill-creator | Create new AI agent skills |
| skill-registry | Update skill registry |
| go-testing | Go tests / Bubbletea TUI testing |
| inditex-apis | Invoke Inditex internal APIs |
| inditex-contract-generator | Generate API contract snapshots |

## Stack-Specific Notes

- **Language**: Kotlin 2.x (source dirs named `java/` — historical)
- **Build**: Maven (no wrapper; `mvn` on PATH)
- **Testing**: JUnit 5 (unit: `*Test.kt`) + MockK (integration: `*IT.kt`)
- **Coverage**: Kover (`mvn test kover:report-html -pl core`)
- **No linter/formatter** configured in Maven
