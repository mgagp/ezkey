# Javadoc Guidelines for Ezkey Project

## Critical Rule: Avoid Complex HTML in Javadoc

**NEVER use `<pre>` blocks with JSON, code samples, or content containing special characters like `{`, `}`, `@`, `<`, `>` in Javadoc comments.** This causes Javadoc parser failures during Maven checkstyle validation.

---

## Problem: Recurring Javadoc Parse Errors

### Common Error Pattern
```
[ERROR] Javadoc comment at column 4 has parse error.
Details: no viable alternative at input '</' while parsing HTML_ELEMENT
```

### Root Cause
Javadoc's HTML parser is strict. When `<pre>` blocks contain:
- Curly braces: `{`, `}`
- At symbols: `@`
- Unescaped angle brackets: `<`, `>`
- Code-like syntax: `&#64;`, etc.

The parser becomes confused trying to interpret these as HTML elements and fails.

---

## Solution: Text-Only Descriptions

### ❌ DON'T - Problematic Patterns

**Pattern 1: JSON in `<pre>` block**
```java
/**
 * Response format:
 * <pre>
 * {
 *   "type": "...",
 *   "status": 400
 * }
 * </pre>
 */
```

**Pattern 2: Code with `{@code}`**
```java
/**
 * Usage:
 * <pre>
 * {@code
 *   @Component
 *   public class MyHandler { ... }
 * }
 * </pre>
 */
```

**Pattern 3: `<pre>` with @ symbols**
```java
/**
 * <pre>
 * &#64;Service
 * &#64;RestControllerAdvice
 * public class Handler { }
 * </pre>
 */
```

### ✅ DO - Correct Patterns

**Pattern 1: Describe structure in plain text**
```java
/**
 * <p><b>RFC 9457 Response:</b> The response includes the following fields:
 * type (problem URI), title (error category), status (HTTP code), detail (error message),
 * and path (request URI).
 */
```

**Pattern 2: Use `{@code}` for inline code only**
```java
/**
 * Response DTOs are returned wrapped in {@code ResponseEntity} with appropriate HTTP status codes.
 */
```

**Pattern 3: Reference via `{@link}` instead of showing code**
```java
/**
 * <p>Subclasses implement exception handler methods annotated with {@code @ExceptionHandler}
 * and delegate response construction to {@link #buildProblemDetail}, passing the exception,
 * HTTP status, problem type URI, human-readable title, and request object.
 */
```

**Pattern 4: Use `<ul>` lists for structured information**
```java
/**
 * <p>The response includes:
 * <ul>
 *   <li><code>type</code> - Problem URI for error categorization</li>
 *   <li><code>title</code> - Human-readable error title</li>
 *   <li><code>status</code> - HTTP status code</li>
 *   <li><code>detail</code> - Specific error message</li>
 * </ul>
 */
```

---

## Checklist for Javadoc Generation

When generating Javadoc for any class, especially exception handlers:

- [ ] **No `<pre>` blocks with JSON or code** - Replace with bullet lists or plain text descriptions
- [ ] **No mixed `{@code}` with `<pre>`** - Use one or the other, not both
- [ ] **No `&#64;`** (escaped @) in `<pre>` - Causes parser ambiguity
- [ ] **No unescaped `{` or `}` in `<pre>`** - Use bullet points instead
- [ ] **Use `{@link}` for references** - Safer than inline code examples
- [ ] **Use `{@code}` only for inline short identifiers** - Not for complex code blocks
- [ ] **Use `<ul>` + `<li>` for lists** - Cleaner than `<pre>` blocks
- [ ] **No nested HTML elements** - Keep structure simple
- [ ] **Test with `mvn checkstyle:check`** - Always validate before commit

---

## Exception Handler Javadoc Template

```java
/**
 * Handles [ExceptionName] and returns HTTP [STATUS_CODE].
 *
 * <p><b>Responsibility:</b> This component intercepts [exception type] exceptions
 * and converts them into RFC 9457 ProblemDetail responses with appropriate HTTP status codes.
 *
 * <p><b>Exceptions Handled ([N] total):</b>
 *
 * <ul>
 *   <li><b>[ExceptionName] ([STATUS]):</b> [Description of when this exception occurs]
 * </ul>
 *
 * <p><b>Response Format:</b> All responses conform to RFC 9457 with the following fields:
 *
 * <ul>
 *   <li><code>type</code> - Problem URI for error categorization
 *   <li><code>title</code> - Human-readable error title
 *   <li><code>status</code> - HTTP status code
 *   <li><code>detail</code> - Specific error message
 *   <li><code>path</code> - Request URI where error occurred
 * </ul>
 *
 * <p><b>Integration:</b> This handler is registered with {@code @RestControllerAdvice}
 * and automatically picked up by Spring's exception handling mechanism.
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ExceptionHandlerBase
 * @see org.springframework.http.ProblemDetail
 */
```

---

## Real-World Examples from Ezkey

### ✅ Good: DomainExceptionHandler
- Uses bullet lists for exception types
- Describes response format in text, not code
- References base class via `@see`
- No `<pre>` blocks with problematic content

### ❌ Bad: Previous ExceptionHandlerBase versions
- Had `<pre>` blocks with JSON structure (causes parse errors)
- Had `<pre>` blocks with code samples containing `@` symbols
- Mixed `{@code}` with `<pre>` (ambiguous)

---

## Migration Guide: Fixing Existing Javadoc

If you find a file with `<pre>` blocks containing JSON or code:

1. **Find the `<pre>` block**
   ```bash
   grep -n "<pre>" src/main/java/**/*.java
   ```

2. **Extract the content** - what was it trying to show?

3. **Replace with text description** - use bullet points or prose

4. **Use `<ul>` + `<li>` for structure** - cleaner and safer

5. **Validate** - run `mvn checkstyle:check`

Example refactoring:
```java
// BEFORE (fails checkstyle)
/**
 * Response:
 * <pre>
 * {
 *   "error": "..."
 * }
 * </pre>
 */

// AFTER (passes checkstyle)
/**
 * <p><b>Response Format:</b> Returns an error object with the following fields:
 * <ul>
 *   <li><code>error</code> - Error message describing what went wrong
 * </ul>
 */
```

---

## Summary

**The Golden Rule for Ezkey Javadoc:**

> If your Javadoc comment needs to show code, JSON, or special characters, **do not use `<pre>` or complex HTML**. Instead, use plain text descriptions with bullet lists (`<ul>` + `<li>`) and inline code references (`{@code}`, `{@link}`).

This rule prevents recurring parse errors during Maven checkstyle validation and keeps Javadoc maintainable and clear.
