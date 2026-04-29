# AGENTS.md — Coding Guidelines for arch-plugin

This document provides instructions for agentic coding tools working in this repository.

---

## Project Overview

`arch-plugin` is a **Maven plugin** that enforces architectural rules on Java/Kotlin codebases.
It follows **Hexagonal Architecture** (Ports and Adapters):

- `core/` — framework-independent library (domain + application layers)
- `plugin/` — Maven plugin infrastructure (adapters, Mojo entry point)

**Languages:** Kotlin 2.x throughout (source dirs are named `java/` historically — ignore that).  
**JVM target:** Java 11. **Runtime JDK:** 21.

---

## Build Commands

```bash
# Build and install all modules (runs tests)
mvn install

# Build, skip tests
mvn install -DskipTests

# Compile only
mvn compile

# Run all tests
mvn test

# Run tests for the core module only
mvn test -pl core

# Run a specific test class
mvn test -pl core -Dtest=PatternMatcherServiceTest

# Run a specific test method
mvn test -pl core -Dtest=PatternMatcherServiceTest#matchesWildcardPatterns

# Run multiple specific tests
mvn test -pl core -Dtest="PatternMatcherServiceTest+RuleEvaluatorServiceTest"

# Run arch-plugin validation on a project
mvn com.cquilez:arch-plugin:1.0-SNAPSHOT:validate
```

There is **no Maven wrapper**; `mvn` must be available on `PATH`.  
There is **no linter or formatter** configured in Maven (no Ktlint, Detekt, Spotless).  
Code style is enforced via IntelliJ inspections described below.

---

## Module Structure

```
arch-plugin/
├── pom.xml                          # Aggregator POM
├── core/                            # Framework-independent library
│   ├── arch-rules.yml              # Architecture rules for self-validation
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/cquilez/arch/
│       │   ├── domain/              # Pure Kotlin data classes, exceptions
│       │   ├── application/
│       │   │   ├── port/            # Interfaces (FilesystemPort, LogPort, ParserPort)
│       │   │   ├── service/         # Application services
│       │   │   └── usecase/         # Use cases (one public method each)
│       │   └── infrastructure/
│       │       └── adapter/         # YamlParserAdapter (snakeyaml lives here)
│       └── test/kotlin/com/cquilez/arch/application/service/
└── plugin/                          # Maven plugin (adapters, Mojo entry point)
    ├── arch-rules.yml              # Architecture rules
    ├── pom.xml
    └── src/main/java/com/cquilez/arch/infrastructure/
        ├── adapter/                 # FilesystemAdapter, LogAdapter
        └── mojo/                    # ValidatorMojo entry point
```

---

## Architecture Rules (Hexagonal)

These rules are enforced by the plugin's own `arch-rules.yml` and by IntelliJ inspections.
**Violations will be flagged at analysis time.**

- `domain` layer: may only import `java.*`, `org.lombok.*`, and other `domain.*` classes.
- `application` layer: may only use `domain.*` and `application.*` — no infrastructure imports.
- `infrastructure` layer: may import `domain.*` and `application.*` — place libraries (e.g., snakeyaml) here.
- No `*Adapter` classes in `domain` or `application` packages.
- No `*Port` or `*UseCase` classes in `domain` or `infrastructure` packages.
- `*UseCase` classes: exactly **one public method**.
- Services must **not inject use cases** — services and use cases are peers.
- Use cases must **not** have `@Service` / `@Component` annotations.
- All classes: max **300 lines**.

---

## Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Classes | `PascalCase` | `RuleEvaluatorService` |
| Functions / methods | `camelCase` | `evaluateRules` |
| Parameters / local vars | `camelCase` | `ruleViolation` |
| Constants (companion obj) | `UPPER_SNAKE_CASE` | `RULES_FILE_NAME` |
| Packages | `lowercase.dot.separated` | `com.cquilez.arch.application.service` |
| Ports | `*Port` suffix | `FilesystemPort` |
| Adapters | `*Adapter` suffix | `FilesystemAdapter` |
| Use cases | `*UseCase` suffix | `AnalyzeProjectUseCase` |
| Services | `*Service` suffix | `PatternMatcherService` |
| **No `*Impl` suffix** | Use a descriptive name | `FilesystemAdapter`, not `FilesystemPortImpl` |

---

## Code Style

### General

- **Kotlin** is the only language used in `src/` (the `src/main/java/` path is historical).
- Prefer `val` over `var`. Local variables must be `val` where possible (IntelliJ warning).
- Use `private` visibility for all members that are not part of the public API.
- All field and method accesses inside a class **must use `this.` qualifier** (IntelliJ warning).
- No star imports (`import com.example.*`) in production code.
- Imports should be fully qualified and ordered manually.

### Data Classes and Domain Model

- Domain objects are exclusively Kotlin `data class`.
- Each domain class exposes a `companion object` with a `fromMap(map: Map<String, Any>?)` factory
  for YAML deserialization. Annotate the cast with `@Suppress("UNCHECKED_CAST")`.
- Use nullable types (`?`) precisely — only where `null` is a valid semantic value.
- Use default parameters freely: `AnalysisConfig(failIfNoRules: Boolean = false)`.

### Error Handling

- Domain-specific errors: extend `RuntimeException` and live under `domain/exception/`.
- All custom exceptions must declare `companion object { private const val serialVersionUID: Long = 1L }`.
- Wrap unexpected checked exceptions with `throw IllegalStateException(message, cause)`.
- At service boundaries, catch and log non-fatal errors; continue processing remaining items
  (graceful degradation).
- Use `runCatching { }.getOrNull()` for operations where a nullable result is acceptable.
- Use `@Throws(MojoExecutionException::class)` on Maven `execute()` methods.

### Kotlin Idioms

- Prefer Kotlin stdlib operators: `groupBy`, `filter`, `mapNotNull`, `flatMap`, `joinToString`,
  `distinct`, `any`, `all`, `firstOrNull`, `takeIf`, `orEmpty`, `isNullOrBlank`.
- Use `LinkedHashMap` deliberately when insertion order must be preserved.
- Use `open class` on use cases to allow subclassing or test overrides where needed.
- Use anonymous object literals (`object : SomePort { ... }`) to stub dependencies in tests
  rather than a mocking library.

---

## Dependency Injection

- `core` module: **pure constructor injection**, zero framework annotations.
- `plugin` module: JSR-330 (`javax.inject`) — `@Named`, `@Singleton`, `@Inject` — for Maven's
  Sisu/Guice container.
- Never use `@Autowired`, `@Service`, `@Component` (no Spring in this project).

---

## Testing

**Framework:** JUnit Jupiter (JUnit 5).  
**Location:** `core/src/test/kotlin/com/cquilez/arch/application/` (subdirectories: `service/`, `usecase/`)

### Test Style

- Unit tests (`*Test.kt`): Stub interfaces with inline anonymous objects:
  ```kotlin
  val fs = object : FilesystemPort {
      override fun readFile(path: String) = "..."
  }
  ```
- Integration tests (`*IT.kt`): Use **MockK** for mocking:
  ```kotlin
  private val log: LogPort = mockk<LogPort>(relaxed = true)
  every { it.info(any()) } answers { capturedLogs.add(args[0] as String) }
  ```
- Use `Files.createTempDirectory()` for integration-style tests that touch the filesystem.
- Instantiate all collaborators directly — no DI container in tests.
- Test class names: `*Test` suffix matching the class under test (e.g., `PatternMatcherServiceTest`).
- Prefer descriptive method names that read as sentences.

### Integration Tests (IT)

Integration tests use the `*IT.kt` suffix and are configured with **maven-failsafe-plugin**:
- `mvn test -pl core` — runs unit tests only (`*Test.kt`), excludes `*IT.kt`
- `mvn verify -pl core` — runs unit tests + integration tests (`*IT.kt`)
- Run a specific IT class: `mvn -pl core -Dit.test=AnalyzeProjectUseCaseIT verify`

**MockK dependency:** `io.mockk:mockk-jvm:1.14.9` (test scope in `core/pom.xml`)

### Kotlin Test Sources

Kotlin test sources in `src/test/kotlin/` require the `build-helper-maven-plugin` configured in `core/pom.xml`:
```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>build-helper-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>add-test-source</id>
            <phase>generate-test-sources</phase>
            <goals><goal>add-test-source</goal></goals>
            <configuration>
                <sources>
                    <source>${project.basedir}/src/test/kotlin</source>
                </sources>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Code Coverage (Kover)

Code coverage is measured with **Kover** (JetBrains Kotlin code coverage).

**Commands:**
```bash
# Run tests with coverage
mvn test -pl core

# Generate coverage reports (XML and HTML)
mvn test -pl core kover:report-xml kover:report-html

# Generate HTML report only
mvn test -pl core kover:report-html
```

**Reports location:** `core/target/site/kover/`

**Important:** Delete old reports before regenerating to avoid stale data:
```bash
rm -rf core/target/site/kover
mvn clean test -pl core kover:report-xml kover:report-html
```

Kover requires `kover:instrumentation` bound to `process-test-classes` phase in `core/pom.xml`:
```xml
<plugin>
    <groupId>org.jetbrains.kotlinx</groupId>
    <artifactId>kover-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>instrument</id>
            <phase>process-test-classes</phase>
            <goals><goal>instrumentation</goal></goals>
        </execution>
    </executions>
</plugin>
```

---

## Key Files

| File | Purpose |
|---|---|
| `core/.../application/usecase/AnalyzeProjectUseCase.kt` | Central orchestrator — loads YAML, validates, evaluates rules |
| `core/.../application/service/RuleEvaluatorService.kt` | Core engine — parses source files, applies all rule types |
| `core/.../application/service/PatternMatcherService.kt` | Glob pattern matching |
| `core/.../application/port/FilesystemPort.kt` | Port — abstracts filesystem I/O |
| `core/.../application/port/ParserPort.kt` | Port — abstracts YAML parsing (implemented by YamlParserAdapter) |
| `core/.../infrastructure/adapter/YamlParserAdapter.kt` | Adapter — wraps snakeyaml, lives in infrastructure layer |
| `plugin/.../infrastructure/mojo/ValidatorMojo.kt` | Maven entry point — wires dependencies, calls use case |
| `plugin/arch-rules.yml` | Architecture rules (DSL reference) |

---

## Things to Avoid

- Do not add `*Impl` class names.
- Do not give use cases more than one public method.
- Do not have services depend on use cases.
- Do not annotate use cases with `@Service` or `@Component`.
- Do not introduce Spring, Quarkus, or any DI framework into `core/`.
- Do not add star imports in production code.
- Do not leave commented-out code blocks (existing ones should be cleaned up over time).
- Do not exceed 300 lines per class.
- Do not access fields or methods without `this.` qualifier inside a class body.
