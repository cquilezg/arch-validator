# arch-validator

![Build](https://github.com/cquilez/arch-validator/actions/workflows/build.yml/badge.svg)
![Version](https://img.shields.io/github/v/release/cquilez/arch-validator)

A Maven plugin that validates your project's architecture by enforcing rules defined in a YAML configuration file.

## What it does

arch-validator scans your Java and Kotlin source files and checks them against architectural rules you define. It can verify:

- Which packages/libraries each layer is allowed to import
- Which layers can depend on which other layers
- Naming conventions for classes (e.g., `*Adapter` must be in infrastructure)
- That certain classes (like UseCases) follow specific patterns
- Maximum file sizes

## Index

- [Quick Start](#quick-start)
- [Installation](#installation)
- [Configuration](#configuration)
- [Configuration Reference](#configuration-reference)
- [Rule Types](#rule-types)
- [Running the Plugin](#running-the-plugin)
- [Plugin Parameters](#plugin-parameters)
- [Interpreting Results](#interpreting-results)
- [Multi-Module Projects](#multi-module-projects)

---

## Quick Start

### 1. Add the plugin to your project

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.cquilez</groupId>
            <artifactId>arch-plugin</artifactId>
            <version>1.0</version>
            <executions>
                <execution>
                    <phase>validate</phase>
                    <goals>
                        <goal>validate</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 2. Create `arch-rules.yml` in your project root

```yaml
layers:
  domain:
    location: com.example.domain
  application:
    location: com.example.application
  infrastructure:
    location: com.example.infrastructure

rules:
  - title: Domain should only use Java and Lombok
    layers: domain
    allowed:
      imports:
        - java.*
        - org.lombok.*
  - title: Application can use domain and application layers
    layers: application
    allowed:
      layers: [domain, application]
```

### 3. Run the validation

```bash
mvn validate
```

---

## Installation

### Maven Central

The plugin is available from Maven Central:

```xml
<plugin>
    <groupId>com.cquilez</groupId>
    <artifactId>arch-plugin</artifactId>
    <version>1.0</version>
</plugin>
```

### Full Plugin Configuration

```xml
<plugin>
    <groupId>com.cquilez</groupId>
    <artifactId>arch-plugin</artifactId>
    <version>1.0</version>
    <configuration>
        <!-- Optional: fail if arch-rules.yml is missing (default: false) -->
        <failIfNoRules>true</failIfNoRules>
        
        <!-- Optional: include test sources in validation (default: false) -->
        <includeTests>true</includeTests>
    </configuration>
    <executions>
        <execution>
            <id>validate-architecture</id>
            <phase>validate</phase>
            <goals>
                <goal>validate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

## Configuration

The `arch-rules.yml` file is the heart of arch-validator. It defines your layers, class types, and the rules they must follow.

### Example: Hexagonal Architecture

```yaml
layers:
  domain:
    location: com.example.domain
  application:
    location: com.example.application
  infrastructure:
    location: com.example.infrastructure

types:
  - name: Adapter
    patterns: ["*Adapter"]
    layer: infrastructure
  - name: Port
    patterns: ["*Port"]
    layer: application
  - name: Service
    patterns: ["*Service"]
    layer: application
  - name: UseCase
    patterns: ["*UseCase"]
    layer: application
  - name: Controller
    patterns: ["*Controller"]
    layer: infrastructure

rules:
  - title: Domain should only use Java and Lombok
    layers: domain
    allowed:
      imports:
        - java.*
        - org.lombok.*
        - com.example.domain.*

  - title: Application can only use domain and application layers
    layers: application
    allowed:
      layers: [domain, application]

  - title: Infrastructure can use any layer
    layers: infrastructure
    allowed:
      layers: [domain, application, infrastructure]

  - title: Domain cannot have adapters
    layers: domain
    forbidden:
      classes: ["*Adapter"]

  - title: Use cases must have exactly one public method
    classes: ["*UseCase"]
    allowed:
      methods:
        public: 1

  - title: Classes should not exceed 300 lines
    allowed:
      max-lines: 300
```

---

## Configuration Reference

### Layers

Define the architectural layers in your project:

```yaml
layers:
  <layer-name>:
    location: <package-prefix>
```

| Property | Description |
|----------|-------------|
| `layer-name` | A descriptive name for your layer (e.g., `domain`, `application`) |
| `location` | The package prefix that belongs to this layer |

All classes whose fully qualified name starts with the location prefix belong to that layer.

### Types

Define class patterns and which layer they belong to:

```yaml
types:
  - name: <TypeName>
    patterns: ["*Pattern"]
    layer: <layer-name>
```

| Property | Description |
|----------|-------------|
| `name` | A descriptive name (used in rule references) |
| `patterns` | Glob patterns to match class names (e.g., `*Adapter`, `*ServiceImpl`) |
| `layer` | Which layer these classes must belong to |

### Rules

Rules define what is allowed or forbidden:

```yaml
rules:
  - title: <Description>
    layers: <layer(s)>     # Optional: apply to specific layers
    classes: <pattern(s)>  # Optional: apply to specific class patterns
    types: <type(s)>       # Optional: apply to specific types
    allowed:              # Optional: what is allowed
      imports: [...]
      layers: [...]
      methods:
        public: <count>
      max-lines: <number>
    forbidden:            # Optional: what is forbidden
      classes: [...]
      types: [...]
```

---

## Rule Types

### Allowed Imports

Restricts which packages a layer can import:

```yaml
- title: Domain should only use Java and Lombok
  layers: domain
  allowed:
    imports:
      - java.*
      - org.lombok.*
```

### Allowed Layers

Restricts which architectural layers a layer can depend on:

```yaml
- title: Application can only use domain layer
  layers: application
  allowed:
    layers: [domain]
```

### Allowed Methods

Restricts the number of public methods in classes:

```yaml
- title: Use cases must have one public method
  classes: ["*UseCase"]
  allowed:
    methods:
      public: 1
```

### Max Lines

Restricts the maximum number of lines per file:

```yaml
- title: Classes should be under 300 lines
  allowed:
    max-lines: 300
```

### Forbidden Classes

Prevents certain class patterns from existing in specified layers:

```yaml
- title: Domain cannot have adapters
  layers: domain
  forbidden:
    classes: ["*Adapter"]
```

### Forbidden Types

Prevents types from using certain other types:

```yaml
- title: Use cases cannot use use cases
  types: [UseCase]
  forbidden:
    types: [UseCase]
```

---

## Running the Plugin

### Automatic (with Maven lifecycle)

Add the plugin to the `validate` phase as shown in [Installation](#installation), then run:

```bash
mvn validate
```

### Manual

Run the plugin directly without binding it to a lifecycle phase:

```bash
mvn com.cquilez:arch-plugin:1.0:validate
```

### With custom parameters

```bash
mvn com.cquilez:arch-plugin:1.0:validate \
    -Darch.failIfNoRules=true \
    -Darch.includeTests=true
```

---

## Plugin Parameters

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| `failIfNoRules` | `arch.failIfNoRules` | `false` | When `true`, fails the build if `arch-rules.yml` is not found |
| `includeTests` | `arch.includeTests` | `false` | When `true`, also validates test source files |

---

## Interpreting Results

### Success

When all rules pass, you'll see:

```
Layers: 3
Types: 5
Rules: 7

Evaluating rules...
Rules evaluation finished.

=== Project analysis ===
Total rules evaluated: 7
Total classes: 42
```

### Violations Found

When a rule is violated, the plugin reports the specific issue:

```
=== Violations by rule ===
Rule: Domain should only use Java and Lombok
  at com.example.domain.MyService(domain/MyService.java:5)
  Cause: Import outside allowed patterns: org.springframework.stereotype.Service
```

If violations are found, the build will **fail** with exit code 1.

---

## Multi-Module Projects

arch-validator automatically handles multi-module Maven projects:

1. It reads the `arch-rules.yml` from the **root project** directory
2. It scans source roots from **all modules**
3. Rules are applied consistently across the entire project

Place your `arch-rules.yml` in the root project directory, and it will apply to all child modules.

---

## Compatibility

| Requirement | Version |
|-------------|---------|
| Java | 11 or higher |
| Kotlin | Supported |
| Maven | 3.6.0 or higher |
| Source files | `.java` and `.kt` |

---

## Troubleshooting

### "No rules file found" warning

The plugin found no `arch-rules.yml` in your project root. Either:
- Create the file (see [Configuration](#configuration))
- Set `failIfNoRules: true` to make this an error

### Classes not being detected

Check your `layers.location` values. They should be the **package prefix**, not the full path. For example:

```yaml
layers:
  domain:
    location: com.example.domain  # Package prefix
```

### Wildcard patterns not matching

Patterns use glob syntax:
- `*Adapter` matches anything ending with "Adapter"
- `*ServiceImpl` matches anything ending with "ServiceImpl"
- Patterns are **case-insensitive** by default
