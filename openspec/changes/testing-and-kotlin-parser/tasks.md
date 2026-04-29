# Tasks: testing-and-kotlin-parser

## Phase 1: Bug Fixes (must run before self-validation)

- [x] 1.1 Fix `core/arch-rules.yml`: rename every `layer:` key to `layers:` and remove duplicate rule title. **Verify**: `mvn com.cquilez:arch-plugin:1.0-SNAPSHOT:validate` on `core/` reports 0 violations.
- [x] 1.2 Fix `plugin/arch-rules.yml`: same `layer:` → `layers:` rename + remove duplicate title. **Verify**: same command on `plugin/` reports 0 violations.

## Phase 2: New Port + Adapter

- [x] 2.1 Create `core/.../application/port/KotlinSourceParserPort.kt` — fun interface with single `parse(content: String): ParsedSource`. **Verify**: compiles, lives in application layer (no infra imports).
- [x] 2.2 Add `org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.20` to `core/pom.xml` (test + compile scope). **Verify**: `mvn compile -pl core` succeeds.
- [x] 2.3 Create `core/.../infrastructure/adapter/KotlinPsiSourceParser.kt` implementing `KotlinSourceParserPort` — wraps PSI KtFile to extract packageName, imports, classes, visibility, publicMethodCount, lineCount. **Verify**: `mvn compile -pl core` succeeds.

## Phase 3: Core Integration

- [x] 3.1 Modify `SourceParserService.kt`: add `kotlinParser: KotlinSourceParserPort` constructor param; replace regex `parseKotlinSource()` and all private helpers with delegation to `kotlinParser.parse()`. **Verify**: `mvn compile -pl core` succeeds, no regex remaining.
- [x] 3.2 Modify `ValidatorMojo.kt`: construct `KotlinPsiSourceParser()` and pass it into `SourceParserService`. **Verify**: `mvn compile -pl plugin` succeeds.

## Phase 4: Core Unit Tests

- [x] 4.1 Create `SourceParserServiceTest.kt` — stub `KotlinSourceParserPort` and `FilesystemPort` with anonymous objects; cover spec scenarios SP-02 through SP-09 (package, imports, classes, visibility, public method count, line count, Java parsing, error handling). **Verify**: `mvn test -pl core -Dtest=SourceParserServiceTest` green.
- [x] 4.2 Update existing `KotlinPsiSourceParserTest.kt` — ensure inline Kotlin strings cover SP-01 (parse real PSI), SP-08 (error returns empty/error ParsedSource), SP-09 (line numbers). **Verify**: `mvn test -pl core -Dtest=KotlinPsiSourceParserTest` green.
- [x] 4.3 Create `RuleValidatorServiceTest.kt` covering AV-01/AV-02 (layer imports, adapter/port/usecase placement). Stub `FilesystemPort`, `LogPort`. **Verify**: tests green.
- [x] 4.4 Extend `PatternMatcherServiceTest.kt` — add glob wildcard scenarios beyond the existing 2 tests. Cover edge cases: empty patterns, single char, double-star, case sensitivity, exact match. **Verify**: `mvn test -pl core -Dtest=PatternMatcherServiceTest` green.
- [x] 4.5 Create `LayerFinderServiceTest.kt` — layer resolution from config. Stub `FilesystemPort` and `PatternMatcherService`. **Verify**: tests green.
- [x] 4.6 Create `YamlParserAdapterTest.kt` — valid YAML parsing, malformed YAML error handling. Stub `FilesystemPort`. **Verify**: tests green.

## Phase 5: Core Integration Tests

- [ ] 5.1 Extend `AnalyzeProjectUseCaseIT.kt` with violation-detection scenario (AV-IT-02): fixture project with an import that breaks layer rules; assert non-empty violations list. **Verify**: `mvn verify -pl core -Dit.test=AnalyzeProjectUseCaseIT` green.
- [ ] 5.2 Add error-path ITs: malformed YAML, `failIfNoRules=true` with no rules file, missing source file, duplicate class names (scenarios AV-IT-03 through AV-IT-05). **Verify**: same command green.
- [ ] 5.3 Enable `@Disabled` on `RuleEvaluatorServiceIT.detectsMaxLinesViolation` and fix if needed. **Verify**: `mvn verify -pl core` — zero `@Disabled` tests.

## Phase 6: Plugin Integration Tests

- [ ] 6.1 Add `maven-invoker-plugin` configuration to `plugin/pom.xml` bound to `integration-test`. **Verify**: `mvn verify -pl plugin` runs invoker phase without error.
- [ ] 6.2 Create fixture `plugin/src/test/resources/it-projects/success-project/` with valid `arch-rules.yml` + minimal Kotlin source; add `invoker.properties`. **Verify**: invoker IT passes.
- [ ] 6.3 Create fixture `it-projects/violation-project/` with an intentional layer violation; `invoker.properties` expects `BUILD FAILURE`. **Verify**: invoker IT passes.
- [ ] 6.4 Create fixture `it-projects/fail-if-no-rules-project/` with no rules file and `<failIfNoRules>true</failIfNoRules>`; expects `BUILD FAILURE`. **Verify**: invoker IT passes.
