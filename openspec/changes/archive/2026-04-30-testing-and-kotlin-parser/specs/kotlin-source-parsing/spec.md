# Kotlin Source Parsing Specification

## Purpose

Defines requirements for parsing `.kt` source files and extracting structural metadata used by architecture rule evaluation.

## Requirements

### Requirement: Kotlin Source Parsing

The system MUST parse `.kt` files and return a `ParsedSource` object containing package, imports, class declarations, and public method counts.

#### Scenario: Parse a valid Kotlin source file

- GIVEN a valid `.kt` file with a package declaration, imports, and class definitions
- WHEN the parser processes the file
- THEN it returns a `ParsedSource` with non-null package, import list, class list, and line count

---

### Requirement: Package Extraction

The system MUST extract the fully-qualified package name from any valid Kotlin source file.

#### Scenario: File with explicit package declaration

- GIVEN a `.kt` file containing `package com.example.domain`
- WHEN the parser processes the file
- THEN `ParsedSource.packageName` equals `"com.example.domain"`

#### Scenario: File with no package declaration

- GIVEN a `.kt` file with no package statement
- WHEN the parser processes the file
- THEN `ParsedSource.packageName` is empty or null

---

### Requirement: Import Extraction

The system MUST extract all imported declarations and their line numbers.

#### Scenario: File with multiple imports

- GIVEN a `.kt` file with three import statements at lines 3, 4, and 5
- WHEN the parser processes the file
- THEN `ParsedSource.imports` contains exactly three entries with matching line numbers

#### Scenario: File with no imports

- GIVEN a `.kt` file with no import statements
- WHEN the parser processes the file
- THEN `ParsedSource.imports` is empty

---

### Requirement: Class, Interface, and Object Detection

The system MUST detect all class, interface, and object declarations, including nested ones.

#### Scenario: Top-level class

- GIVEN a `.kt` file containing one top-level class
- WHEN the parser processes the file
- THEN `ParsedSource.classes` contains one entry with the correct name

#### Scenario: Nested class inside a class

- GIVEN a `.kt` file with a class containing an inner class
- WHEN the parser processes the file
- THEN `ParsedSource.classes` contains both the outer and inner class entries

#### Scenario: Interface and object declarations

- GIVEN a `.kt` file containing an interface and a companion object
- WHEN the parser processes the file
- THEN both are present in `ParsedSource.classes`

---

### Requirement: Visibility Detection

The system MUST correctly identify `public`, `protected`, `internal`, and `private` visibility modifiers. Methods with `override` and no explicit modifier MUST be treated as implicitly public.

#### Scenario: Explicit visibility modifiers

- GIVEN a class with methods annotated `public`, `protected`, `internal`, and `private`
- WHEN the parser processes the file
- THEN each method is recorded with its correct visibility

#### Scenario: Override method with no visibility modifier

- GIVEN a method declared as `override fun foo()`
- WHEN the parser processes the file
- THEN the method is treated as `public`

---

### Requirement: Public Method Counting

The system MUST count public methods per class, including functions with `override` and no explicit modifier.

#### Scenario: Class with mixed visibility methods

- GIVEN a class with two public methods, one private, and one `override fun` with no modifier
- WHEN the parser processes the file
- THEN the public method count for that class is 3

---

### Requirement: Line Count

The system MUST report the total number of lines in the source file.

#### Scenario: Known-size file

- GIVEN a `.kt` file with exactly 42 lines
- WHEN the parser processes the file
- THEN `ParsedSource.lineCount` equals 42

---

### Requirement: Error Handling

The system MUST handle malformed Kotlin source gracefully and return an error result. It MUST NOT crash or throw an unhandled exception.

#### Scenario: Malformed Kotlin input

- GIVEN a `.kt` file containing invalid Kotlin syntax
- WHEN the parser processes the file
- THEN the result signals a parse error (e.g., returns null or an error-bearing result)
- AND no exception propagates to the caller

---

### Requirement: Line Number Accuracy

The system MUST report line numbers that match the original source file (1-indexed).

#### Scenario: Import on known line

- GIVEN a `.kt` file where an import statement is on line 5
- WHEN the parser processes the file
- THEN the corresponding import entry has `lineNumber == 5`
