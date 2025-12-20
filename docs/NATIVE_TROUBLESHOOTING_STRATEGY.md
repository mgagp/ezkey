# Native Image Troubleshooting Strategy

This document outlines the systematic approach for resolving native image compilation and runtime issues in the Ezkey project.

## Core Principle

**Always validate if selective disablement is possible before implementing complex workarounds.**

When encountering a native image issue, the first step should be to check if the problematic feature can be disabled via configuration properties. This approach often provides the fastest path to a working native image.

## Decision Tree

```
Encounter Native Image Issue
    │
    ├─→ Check for disablement property/configuration
    │       │
    │       ├─→ Property exists?
    │       │       │
    │       │       ├─→ YES: Disable and test
    │       │       │       │
    │       │       │       ├─→ Application fully functional?
    │       │       │       │       │
    │       │       │       │       ├─→ YES: Category 1 (Document implications)
    │       │       │       │       │
    │       │       │       │       └─→ NO: Category 2 (Note for later review)
    │       │       │       │
    │       │       │       └─→ Issue resolved?
    │       │       │               │
    │       │       │               ├─→ YES: Continue with next issue
    │       │       │               └─→ NO: Implement workaround
    │       │       │
    │       │       └─→ NO: Implement workaround (reflection hints, etc.)
    │       │
    │       └─→ NO: Implement workaround (reflection hints, etc.)
```

## Category Classification

### Category 1: Disablement → Fully Functional Application

**Definition**: Disabling the feature results in a fully functional, production-ready application.

**Action**:
1. ✅ Document the disablement in this file
2. ✅ Assess and document functional implications
3. ✅ Note any limitations or trade-offs
4. ✅ Mark as acceptable for production (if applicable)
5. ✅ Create a review task for future optimization (if needed)

**Example**: Disabling SQL AST tree logging when it's not needed for production operations.

### Category 2: Disablement → Non-Production-Ready Application

**Definition**: Disabling the feature resolves the native image issue but results in a non-production-ready application (missing functionality, degraded performance, etc.).

**Action**:
1. ✅ Document the disablement in this file
2. ✅ Clearly mark as **NOT production-ready**
3. ✅ Document what functionality is lost
4. ✅ Note for review after achieving a working native image
5. ⚠️ Do NOT deploy to production with this disablement

**Example**: Disabling a critical security feature that's required for production but causes native image issues.

## Documentation Template

For each disablement, document:

```markdown
### [Feature Name] - [Date]

**Issue**: [Description of the native image issue]

**Disablement Property**: [Property name and value]

**Category**: [1 or 2]

**Functional Impact**: [What functionality is affected]

**Production Ready**: [YES/NO]

**Review Status**: [PENDING/REVIEWED/ACCEPTED]

**Notes**: [Any additional context]
```

## Current Disablements

### SQL AST Tree Logger - 2025-12-19

**Issue**: `Invalid logger interface org.hibernate.sql.ast.tree.SqlAstTreeLogger (implementation not found)` - JBoss Logging generated class `SqlAstTreeLogger_$logger` not found in native image. The logger is initialized statically during class loading, causing the error even if logging is disabled.

**Disablement Attempts** (All Failed):
- ❌ `spring.jpa.properties.hibernate.sql.log_sql_ast=false` - **FAILED**: Property read too late, class initializes statically before property is read
- ❌ `logging.level.org.hibernate.orm.sql.ast.tree=OFF` - **FAILED**: Logging level doesn't prevent static initialization
- ❌ `logging.level.org.hibernate.sql.ast.tree=OFF` - **FAILED**: Logging level doesn't prevent static initialization
- ❌ Reflection hints for `SqlAstTreeLogger` and `SqlAstTreeLogger_$logger` - **FAILED**: Generated class not found at build time
- ❌ `--initialize-at-run-time=org.hibernate.sql.ast.tree` - **FAILED**: Class still initializes and fails at runtime

**Root Cause**: 
- `SqlAstTreeLogger` has a static initializer (`<clinit>`) that runs when the class is first loaded
- The static initializer calls `Logger.getMessageLogger()` which tries to find the generated class `SqlAstTreeLogger_$logger`
- This happens before any configuration properties are read
- The generated class is created by JBoss Logging annotation processor at compile time, but GraalVM native image doesn't include it

**Category**: **Category 2** (Disablement failed - requires alternative solution)

**Functional Impact**: 
- **Application cannot start** - This is a blocking issue
- SQL AST tree debugging/logging is a development feature, but the logger initialization is required for Hibernate to function
- Core Hibernate functionality is blocked by this initialization failure

**Production Ready**: **NO** (Application fails to start)

**Review Status**: **REQUIRES_ALTERNATIVE_SOLUTION**

**Next Steps** (When resuming work):
1. **Verify generated class existence**: 
   - Search for `SqlAstTreeLogger_$logger` in Hibernate dependencies (JAR files)
   - Check if the class exists but is not being included in native image
   - Use `-H:+TraceClassInitialization` to trace class loading behavior

2. **Investigate JBoss Logging class generation**: 
   - Find where `SqlAstTreeLogger_$logger` should be generated (annotation processor)
   - Ensure generated classes are included in native image build
   - Check if there's a way to force inclusion of generated classes

3. **Check Hibernate version compatibility**: 
   - Verify if newer/older Hibernate versions have this issue
   - Check Hibernate release notes for native image compatibility improvements
   - Consider upgrading/downgrading Hibernate if a compatible version exists

4. **Explore GraalVM substitution mechanism**: 
   - Use GraalVM's `@Substitute` annotation to replace problematic logger initialization
   - Create a substitution class that provides a no-op logger implementation
   - This is a more advanced solution but may be necessary

5. **Research community solutions**: 
   - Search Spring Boot + Hibernate + GraalVM native image issues
   - Check Quarkus implementation (they use Hibernate with native images)
   - Look for existing workarounds or patches

6. **Alternative approaches**:
   - Consider if this logger can be completely bypassed via Hibernate configuration
   - Evaluate if a custom Hibernate build without this logger is feasible
   - Assess if switching ORM is an option (not recommended unless absolutely necessary)

**Notes**: 
- This is a fundamental incompatibility between JBoss Logging's annotation processor and GraalVM native image
- The static initialization pattern used by JBoss Logging is problematic for native images
- The logger initializes during class loading, before any configuration properties are read
- This issue blocks native image compilation for auth-api
- All disablement attempts via properties have failed

## Implementation Guidelines

### When to Use This Strategy

1. **First Response**: Always check for disablement options first
2. **Complex Workarounds**: If a workaround requires extensive reflection hints or native image configuration, check for disablement first
3. **Non-Critical Features**: Features that are not essential for core functionality are good candidates
4. **Development Tools**: Debugging, logging, and development-only features are prime candidates

### When NOT to Use This Strategy

1. **Core Functionality**: Never disable features that are essential for application operation
2. **Security Features**: Security-related features should not be disabled without thorough assessment
3. **Data Integrity**: Features that ensure data consistency should not be disabled
4. **User-Facing Features**: Features visible to end users should not be disabled without user impact assessment

## Review Process

### After Achieving Working Native Image

1. Review all Category 2 disablements
2. Prioritize based on functional impact
3. Implement proper solutions (reflection hints, native image configuration)
4. Re-enable features and validate
5. Update documentation

### Periodic Review

- Review Category 1 disablements quarterly
- Assess if workarounds are now available
- Evaluate if disablement is still acceptable
- Update production readiness status

## Success Criteria

The ultimate goal is:

✅ **Native image that starts without errors**  
✅ **Fully functional application**  
✅ **All end-to-end tests pass (ezkey-tests)**  
✅ **Production-ready deployment**

## Related Documentation

- `docs/NATIVE_COMPILATION_STRATEGY.md` - Overall native compilation strategy
- `docs/NATIVE_BUILD_QUICKSTART.md` - Quick reference for native builds
- `ezkey-auth-api/NATIVE_BUILD.md` - Auth API specific native build documentation
