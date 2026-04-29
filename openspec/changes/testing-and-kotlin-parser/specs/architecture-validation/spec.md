# Delta for Architecture Validation

## ADDED Requirements

### Requirement: Core Service Unit Test Coverage

All core application services MUST have unit tests covering every public method. Tests MUST use inline anonymous objects to stub ports — no mocking library required.

#### Scenario: Service method exercised by unit test

- GIVEN a core service with N public methods
- WHEN the unit test suite runs
- THEN each public method has at least one dedicated test case

---

### Requirement: AnalyzeProjectUseCase Integration Test Coverage

The `AnalyzeProjectUseCase` MUST have integration tests covering both the success path and all known error paths.

#### Scenario: Violation detected path

- GIVEN a project directory whose sources violate at least one architecture rule
- WHEN the use case is executed
- THEN the result contains at least one rule violation

#### Scenario: Malformed YAML config

- GIVEN an `arch-rules.yml` file with invalid YAML syntax
- WHEN the use case is executed
- THEN an error is reported and no violation list is returned

#### Scenario: failIfNoRules flag raised

- GIVEN an `arch-rules.yml` with no rules defined and `failIfNoRules: true`
- WHEN the use case is executed
- THEN the use case signals a failure condition

#### Scenario: Missing arch-rules file

- GIVEN a project directory with no `arch-rules.yml` present
- WHEN the use case is executed
- THEN an appropriate error is reported

#### Scenario: Duplicate rule definitions

- GIVEN an `arch-rules.yml` containing two rules with identical names
- WHEN the use case is executed
- THEN the duplicate is detected and reported as an error

---

### Requirement: Individual Rule Validator Integration Test Coverage

Every individual rule validator MUST have integration tests covering the match path (violation detected) and the non-match path (no violation).

#### Scenario: Rule matches — violation detected

- GIVEN source code that violates a specific rule
- WHEN that rule's validator is executed
- THEN the validator reports a violation for the offending element

#### Scenario: Rule does not match — no violation

- GIVEN source code that complies with a specific rule
- WHEN that rule's validator is executed
- THEN the validator reports no violations

---

### Requirement: Plugin Module Integration Test Coverage

The `plugin` module MUST have integration tests verifying Maven plugin execution, parameter wiring, and exception mapping.

#### Scenario: Plugin executes successfully on a valid project

- GIVEN a Maven project wired with the arch-plugin and a valid `arch-rules.yml`
- WHEN the plugin `validate` goal is executed
- THEN execution completes without error

#### Scenario: Parameter wiring — configurable fields are passed through

- GIVEN the plugin configured with non-default parameter values
- WHEN the plugin executes
- THEN the use case receives the expected parameter values

#### Scenario: Rule violation causes MojoExecutionException

- GIVEN a project that violates architecture rules
- WHEN the plugin `validate` goal is executed
- THEN a `MojoExecutionException` is thrown with a descriptive message
