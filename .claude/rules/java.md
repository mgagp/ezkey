---
paths:
  - "**/*.java"
---

# Java conventions (Ezkey)

Checkstyle and Spotless enforce most style in `./scripts/build.sh`; these lines save a failed build
or a review round.

- **Injection:** constructor injection. Use `@Autowired` only where Spring does not construct the
  object (for example a JPA `EntityListener`); anywhere else, explain why and ask first.
- **Javadoc:** complete on public types and members. On a **class, interface or enum**, never put
  `@param` / `@return` / `@throws` in the type Javadoc (Checkstyle `JavadocType` fails); document
  constructors or methods instead. On a **record**, document every component with `@param` in the
  type Javadoc (required since Checkstyle 13.9).
- **`@since`:** new types get `@since <current calendar year>`. Never change `@since` on an existing
  type.
- **Unused lambda parameters** are the unnamed variable `_` (Checkstyle
  `UnusedLambdaParameterShouldBeUnnamed`; JDK 25): `(root, _, cb) -> …`.
- **New `org.ezkey.<package>` in `ezkey-core` with Spring beans:** add it to `scanBasePackages` in
  `AdminApplication`, `AuthApplication` and `IntegrationApiApplication`. A miss compiles and passes
  unit tests, then fails at startup.
- **Validation:** `./scripts/build.sh` from Git Bash first (full reactor; Checkstyle depends on the
  reactor-built `checkstyle-config`). Targeted `mvn … -pl <module>` only after that baseline has
  passed once. Details: `docs/DEVELOPMENT.md` § *Reliable Local Maven Validation*.
