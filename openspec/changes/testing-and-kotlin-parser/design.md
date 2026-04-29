# Design: Testing + Kotlin PSI Parser

## Technical Approach

Replace the line-by-line regex Kotlin parser inside `SourceParserService` with a PSI-based adapter (`KotlinPsiSourceParser`) that implements a new `KotlinSourceParserPort`. `SourceParserService` delegates Kotlin parsing to this port; the Java path (JavaParser) is unchanged. All existing callers receive the same `ParsedSource` contract — no breaking change. Tests are added at every layer using the existing anonymous-stub pattern for unit tests and MockK for ITs.

---

## Architecture Decisions

| # | Decision | Choice | Rejected | Rationale |
|---|----------|--------|----------|-----------|
| 1 | PSI adapter location | `core/.../infrastructure/adapter/KotlinPsiSourceParser` | `application/service/` | Mirrors `YamlParserAdapter`; infrastructure layer is the designated home for third-party library wrappers |
| 2 | New port vs. reuse `ParserPort` | New `KotlinSourceParserPort` returning `ParsedSource` | Overload `ParserPort<T>` | `ParserPort` returns `Map<String,Any>` (YAML shape). Kotlin parsing returns a typed domain object — a different contract. A dedicated port keeps the type boundary clean |
| 3 | Kotlin compiler version | Pin `kotlin-compiler-embeddable` to `2.1.20` (same as project Kotlin) | Independent version | PSI API compatibility; avoids classpath clashes in fat-jar. Already validated in proposal |
| 4 | `ParsedSource` contract | Frozen — no field changes | Add `isInterface`, `isObject` flags | Specs require visibility and type detection; these are derivable inside the adapter without leaking PSI types. Existing consumers remain untouched |
| 5 | Removal of regex path | Hard delete `parseKotlinSource()` and all private helpers | Feature-flag or parallel | No callers outside `SourceParserService`; PSI is strictly more accurate. Zero backwards-compat risk — internal implementation detail |
| 6 | Plugin Invoker ITs | Add `maven-invoker-plugin` in `plugin/pom.xml` | Mock Mojo in unit tests | Proposal explicitly requires plugin-level ITs; Invoker is the Maven standard for Mojo integration testing |

---

## Data Flow

```
ValidatorMojo.execute()
  └─ SourceParserService(log, filesystem, kotlinParser)   ← new constructor param
       ├─ .kt path  → KotlinPsiSourceParser.parse(content): ParsedSource
       │               (kotlin-compiler-embeddable PSI)
       └─ .java path → JavaParser (unchanged)
                       → ParsedSource

KotlinSourceParserPort (application/port/)
  └─ KotlinPsiSourceParser (infrastructure/adapter/)   ← implements port
```

`ValidatorMojo.executeUseCase()` constructs `KotlinPsiSourceParser()` and passes it to `SourceParserService`. No change to `AnalyzeProjectUseCase` or `RuleEvaluatorService`.

---

## File Changes

| File | Action | Reason |
|------|--------|--------|
| `core/.../application/port/KotlinSourceParserPort.kt` | **Create** | New port interface; keeps PSI out of application layer |
| `core/.../infrastructure/adapter/KotlinPsiSourceParser.kt` | **Create** | PSI implementation of `KotlinSourceParserPort` |
| `core/.../application/service/SourceParserService.kt` | **Modify** | Add `kotlinParser: KotlinSourceParserPort` constructor param; delete `parseKotlinSource()` and all private helpers; delegate to port |
| `plugin/.../mojo/ValidatorMojo.kt` | **Modify** | Construct `KotlinPsiSourceParser()` and pass to `SourceParserService` |
| `core/pom.xml` | **Modify** | Add `kotlin-compiler-embeddable:2.1.20` (compile scope) |
| `plugin/pom.xml` | **Modify** | Add `maven-invoker-plugin`; add `maven-failsafe-plugin` for Invoker ITs |
| `core/.../arch-rules.yml` | **Modify** | Fix `layer:` → `layers:`; remove duplicate rule title |
| `plugin/arch-rules.yml` | **Modify** | Same bug fixes |
| `core/src/test/kotlin/.../service/SourceParserServiceTest.kt` | **Create** | Unit tests: stub `KotlinSourceParserPort` inline |
| `core/src/test/kotlin/.../adapter/KotlinPsiSourceParserTest.kt` | **Create** | Unit tests for adapter (no mock needed — pure function) |
| `core/src/test/kotlin/.../service/RuleEvaluatorServiceIT.kt` | **Modify** | Enable `detectsMaxLinesViolation`; wire `KotlinPsiSourceParser` |
| `core/src/test/kotlin/.../usecase/AnalyzeProjectUseCaseIT.kt` | **Create** | 5 scenarios from spec (happy path, no-rules, violation, etc.) |
| `plugin/src/test/resources/it-projects/` | **Create** | Minimal Maven projects for Invoker ITs |
| `plugin/src/test/resources/invoker.properties` | **Create** | Invoker configuration |

---

## Interfaces / Contracts

```kotlin
// core/.../application/port/KotlinSourceParserPort.kt
package com.cquilez.arch.application.port

import com.cquilez.arch.application.service.SourceParserService.ParsedSource

fun interface KotlinSourceParserPort {
    fun parse(content: String, totalLines: Int): ParsedSource
}
```

`SourceParserService.parseSource()` updated signature (internal only):
```kotlin
class SourceParserService(
    private val log: LogPort,
    private val filesystem: FilesystemPort,
    private val kotlinParser: KotlinSourceParserPort   // NEW
)
```

`ParsedSource` data class stays **exactly as-is** — same fields, same nested types (`ImportRef`, `TypeRef`).

---

## Testing Strategy

| Layer | What | Approach |
|-------|------|----------|
| Unit — `KotlinPsiSourceParserTest` | PSI parses package, imports, classes, visibility, method count, line count correctly | Plain JUnit 5; inline Kotlin source strings as input; no stubs needed |
| Unit — `SourceParserServiceTest` | `.kt` path delegates to port; `.java` path still uses JavaParser; unknown extension returns null; parse error logs warn and returns null | Stub `KotlinSourceParserPort` as anonymous object; stub `FilesystemPort` inline |
| Unit — remaining services | `RuleValidatorService`, `PatternMatcherService`, `LayerFinderService`, `YamlParserAdapter` | Same inline-stub pattern as `PatternMatcherServiceTest` |
| IT — `AnalyzeProjectUseCaseIT` | Full pipeline: no-rules-file (warn), rules-found + no violations, rules-found + violations detected, `failIfNoRules=true` throws, `includeTests` flag | `Files.createTempDirectory()`; MockK for `LogPort`; real `KotlinPsiSourceParser` |
| IT — `RuleEvaluatorServiceIT` | Enable `detectsMaxLinesViolation` | Wire real `KotlinPsiSourceParser` |
| IT — Plugin Invoker | Mojo runs on a minimal project, exits with 0; runs with violations, exits with 1; `failIfNoRules=true` without rules file, exits with 1 | `maven-invoker-plugin`; minimal pom + source tree fixtures under `plugin/src/test/resources/` |

---

## Migration / Rollout

`parseKotlinSource()` is private and has zero callers outside `SourceParserService`. Deletion is safe within a single commit. No feature flag, no staged rollout. Both modules' `arch-rules.yml` bugs (`layer:` → `layers:`) must be fixed **before** running self-validation — otherwise the plugin silently skips all layering rules.

---

## Open Questions

- None — all decisions above are unblocked.
