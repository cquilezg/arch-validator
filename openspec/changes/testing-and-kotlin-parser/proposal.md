# Proposal: Testing Strategy + Kotlin Parser Migration

## Intent

Achieve publish-readiness for the Maven plugin by closing all P0 testing gaps, replacing the brittle regex-based Kotlin parser with a proper AST library, and fixing two silent bugs in `arch-rules.yml` that cause rules to never fire.

## Scope

### In Scope
- Fix `arch-rules.yml` bugs in both `core/` and `plugin/` (duplicate title, `layer:` → `layers:`)
- P0 unit tests: `AnalyzeProjectUseCase`, `RuleValidatorService`, `SourceParserService`
- P1 unit tests: `PatternMatcherService`, `LayerFinderService`, `YamlParserAdapter`
- Enable and fix `RuleEvaluatorServiceIT.detectsMaxLinesViolation` (@Disabled)
- Plugin integration tests via Maven Invoker (`ValidatorMojo`, `LogAdapter`, `FilesystemAdapter`)
- Replace regex `parseKotlinSource()` with `kotlin-compiler-embeddable` PSI adapter

### Out of Scope
- Publishing pipeline / Maven Central release
- New architectural rules beyond existing spec
- Performance benchmarking of the new parser

## Capabilities

### New Capabilities
- `kotlin-source-parsing`: PSI-based Kotlin source analysis via `kotlin-compiler-embeddable`, replacing regex in `SourceParserService` — same `ParsedSource` output contract

### Modified Capabilities
- `architecture-validation`: existing validation behavior now fully covered by unit + integration tests; no behavioral change

## Approach

1. **Bug fixes first** — patch `arch-rules.yml` files so the plugin self-validates correctly
2. **PSI adapter** — add `KotlinPsiSourceParser` in `core/.../infrastructure/adapter/` using `kotlin-compiler-embeddable:2.1.20`; wire via `ParserPort`; delete regex fallback
3. **Core unit tests** — anonymous-object stubs, one class per service under test
4. **Core ITs** — `AnalyzeProjectUseCaseIT` covering violation detection and error paths; enable `detectsMaxLinesViolation`
5. **Plugin ITs** — Maven Invoker Plugin (`maven-invoker-plugin`) with fixture projects in `plugin/src/it/`

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `core/.../infrastructure/adapter/KotlinPsiSourceParser.kt` | New | PSI-based Kotlin parser |
| `core/.../application/service/SourceParserService.kt` | Modified | Delegate to new adapter |
| `core/pom.xml` | Modified | Add `kotlin-compiler-embeddable` dep |
| `core/src/test/kotlin/.../service/*Test.kt` | New | P0+P1 unit tests |
| `core/src/test/kotlin/.../usecase/*IT.kt` | Modified/New | Violation + error-path ITs |
| `plugin/src/it/` | New | Maven Invoker fixture projects |
| `plugin/pom.xml` | Modified | Add `maven-invoker-plugin` |
| `core/arch-rules.yml`, `plugin/arch-rules.yml` | Modified | Fix `layer:` → `layers:`, remove dup title |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `kotlin-compiler-embeddable` version drift from project Kotlin | Low | Pin to same `2.1.20` used in build |
| 55 MB footprint increase on plugin JAR | Med | Document in README; acceptable for a Maven plugin |
| Invoker ITs slow CI | Low | Scope to `verify` phase, exclude from `test` |

## Rollback Plan

`git revert` the PSI adapter commit; re-enable regex path. Bug fixes are independent and safe to keep.

## Success Criteria

- [ ] `mvn verify -pl core` — all unit tests + ITs green, zero @Disabled
- [ ] `mvn verify -pl plugin` — Invoker ITs pass on at least 2 fixture projects
- [ ] `mvn com.cquilez:arch-plugin:validate` on `core/` reports zero violations (self-validation)
- [ ] Kover line coverage ≥ 90% on `core` module
- [ ] No regex in `SourceParserService` or any parser path
