# Development Tools Configuration

This directory contains configuration files for code formatting and style checking tools used in the Ezkey project.

## Files

### `eclipse_format.xml`
Eclipse code formatter profile aligned with Google Java Format style (2 spaces indentation, 100 character line length).

**How to import in Eclipse:**
1. Open Eclipse
2. Go to **Window → Preferences** (or **Eclipse → Preferences** on Mac)
3. Navigate to **Java → Code Style → Formatter**
4. Click **Import...**
5. Select `docs/dev-tools/eclipse_format.xml`
6. Click **Apply and Close**

**How to import in IntelliJ IDEA:**
1. Open IntelliJ IDEA
2. Go to **File → Settings** (or **IntelliJ IDEA → Preferences** on Mac)
3. Navigate to **Editor → Code Style**
4. Click the gear icon next to **Scheme** dropdown
5. Select **Import Scheme → Eclipse XML Profile**
6. Select `docs/dev-tools/eclipse_format.xml`
7. Click **Apply** and **OK**

### `google_checks.xml`
Checkstyle configuration file based on Google Java Style Guide. This file is automatically used by Maven Checkstyle Plugin in all modules.

**Configuration:**
- Validates code style rules (indentation, spacing, naming conventions)
- Enforces Google Java Style Guide standards
- Line length limit: 100 characters
- Validates both main and test source directories

### `checkstyle-header.txt`
Checkstyle header template for source files (if used).

## Formatting Standards

All code formatting follows the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html):

- **Indentation**: 2 spaces (not tabs)
- **Line Length**: 100 characters maximum
- **Encoding**: UTF-8 without BOM
- **Line Endings**: LF (Unix style)
- **Annotations**: Single parameterless annotations may appear on the same line as field/method declarations (aligned with Google Java Format behavior)

## Tools

### Spotless (Maven Plugin)
Automatically formats code using Google Java Format 1.33.0:
- Formats `src/main/java/**/*.java`
- Formats `src/test/java/**/*.java`
- Runs automatically on build via `spotless:apply` goal
- Checkstyle configuration is aligned with Google Java Format behavior

**Usage:**
```bash
# Format all code
mvn spotless:apply

# Check formatting (without applying)
mvn spotless:check
```

### Checkstyle (Maven Plugin)
Validates code style compliance:
- Uses `checkstyle-config` module configuration (aligned with Google Java Format)
- Validates both main and test sources
- Fails build on violations
- Configuration matches Google Java Format behavior (e.g., single parameterless annotations on same line)

**Usage:** on a machine that has already built this reactor at least once:

```bash
mvn checkstyle:check
```

On a **fresh clone**, use `./scripts/build.sh` instead. Checkstyle is not a plugin you install from a remote repo: it loads `org.ezkey:checkstyle-config` from the local Maven repository. See [`docs/DEVELOPMENT.md`](../DEVELOPMENT.md) § *First clone on a new workstation*.

### Checkstyle (Eclipse Plugin Integration)
Eclipse reads the `.checkstyle` files stored in each module and points them to the shared configuration in the repository root.

- Each module references the parent file with a relative path (`file:../checkstyle.xml`). Refresh the projects if Eclipse reports that the file cannot be found.
- Ensure the repository is imported as a set of existing projects so that each module keeps its `.checkstyle` metadata.
- After importing, open **Window → Preferences → Checkstyle** to confirm that the configuration named **Ezkey Root Checkstyle** is listed without errors.
- The cache is stored per-module in `target/checkstyle-cachefile`, so no additional setup is required once the projects are refreshed.

## Aligning IDE with Build Tools

To ensure your IDE formatter matches Spotless:

1. **Import the Eclipse formatter** (see instructions above)
2. **Configure your IDE to format on save** (recommended)
3. **Run `mvn spotless:apply`** before committing to ensure consistency

## Troubleshooting

### Spotless and IDE formatter produce different results
- Make sure you've imported the correct `eclipse_format.xml` file
- Verify IDE formatter uses 2 spaces indentation, not 4
- Run `mvn spotless:apply` to align code with build configuration

### Checkstyle violations after Spotless
- Checkstyle configuration is aligned with Google Java Format
- Run `mvn spotless:apply` first, then `mvn checkstyle:check`
- Both tools use the same formatting rules - violations should be rare

