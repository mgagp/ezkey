# Pagination and Sorting Guidelines

## Overview

This document describes the standardized approach for implementing pagination and sorting in Ezkey REST APIs. This pattern ensures consistency, maintainability, and adherence to Spring Boot best practices.

## Standard Pattern

### Controller Layer

**Pattern**: Use `Pageable` directly in controller method signatures with `@PageableDefault` annotation.

```java
@GetMapping
public ResponseEntity<Page<EntityDto>> search(
    // ... filter parameters ...
    @ParameterObject
    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
    Pageable pageable) {
    
    Page<EntityDto> results = service.findByFilters(..., pageable)
        .map(mapper::toDto);
    
    return ResponseEntity.ok(results);
}
```

**Key Points**:
- `@PageableDefault` defines default values (size, sort field, direction)
- `@ParameterObject` (SpringDoc) automatically documents `page`, `size`, and `sort` parameters in Swagger
- Spring Boot automatically resolves `?page=0&size=20&sort=field,direction` into `Pageable`
- No manual `PageRequest.of()` construction needed
- No manual size validation needed (Spring handles it)

### Service Layer

**Pattern**: Use `Specification` with `JpaSpecificationExecutor` for dynamic queries.

```java
@Transactional(readOnly = true)
public Page<Entity> findByFilters(
    // ... filter parameters ...
    Pageable pageable) {
    
    Specification<Entity> spec = (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();
        
        // Add filter predicates based on non-null parameters
        if (filterParam != null) {
            predicates.add(cb.equal(root.get("field"), filterParam));
        }
        
        // Apply default sort ONLY if pageable is unsorted
        if (pageable.getSort().isUnsorted()) {
            query.orderBy(cb.desc(root.get("createdAt")));
        }
        
        return cb.and(predicates.toArray(new Predicate[0]));
    };
    
    return repository.findAll(spec, pageable);
}
```

**Key Points**:
- Default sort is applied in `Specification`, not in controller
- Only apply default sort if `pageable.getSort().isUnsorted()`
- This allows dynamic sorting from API while providing sensible defaults

### Repository Layer

**Pattern**: Extend `JpaSpecificationExecutor` to enable `Specification` support.

```java
@Repository
public interface EntityRepository 
    extends JpaRepository<Entity, Integer>, JpaSpecificationExecutor<Entity> {
    // No custom findByFilters method needed - use findAll(Specification, Pageable)
}
```

**Key Points**:
- `JpaSpecificationExecutor` provides `findAll(Specification<T>, Pageable)` method
- No need for `@Query` annotations for dynamic filtering
- More flexible and maintainable than JPQL queries

## API Usage Examples

### Basic Pagination

```
GET /api/v1/entities?page=0&size=20
```

### Sorting

```
GET /api/v1/entities?sort=fieldName,asc
GET /api/v1/entities?sort=fieldName,desc
GET /api/v1/entities?sort=createdAt,desc&sort=id,asc  # Multiple sorts
```

### Combined Filters, Pagination, and Sorting

```
GET /api/v1/entities?status=PENDING&page=0&size=10&sort=authAttemptId,asc
```

## Default Values

**Standard Defaults**:
- `page`: 0 (zero-based)
- `size`: 20
- `sort`: `createdAt,DESC` (newest first)

**Rationale**:
- Size of 20 balances performance and usability
- Sorting by creation date descending shows most recent items first (operational relevance)
- These values are reasonable defaults that rarely need to change

## Constants vs Configuration

**Decision**: Use `@PageableDefault` with hardcoded values (standard Spring Boot practice).

**Rationale**:
- Standard Spring Boot approach
- Values (20, createdAt DESC) are reasonable and unlikely to change
- Simpler and more maintainable than externalizing via `@ConfigurationProperties`
- Can be externalized later without breaking changes if needed

## Validation

### Pageable Validation

Spring Boot automatically validates `Pageable` parameters:
- `page` must be >= 0
- `size` must be > 0

If custom validation is needed (e.g., max size limit), create a custom `Pageable` wrapper:

```java
public class ValidatedPageable {
    @Min(0)
    private int page;
    
    @Min(1)
    @Max(100)
    private int size;
    
    // ... getters, setters
}
```

**Note**: For nested validation, ensure `@Valid` is used correctly and that `@ExceptionHandler(MethodArgumentNotValidException.class)` handles nested validation errors properly.

## Swagger/OpenAPI Documentation

### Using @ParameterObject

The `@ParameterObject` annotation from SpringDoc automatically documents pagination parameters:

```java
@ParameterObject Pageable pageable
```

This generates Swagger documentation for:
- `page` (integer, default: 0)
- `size` (integer, default: 20)
- `sort` (array of strings, e.g., ["field,direction"])

### Operation Documentation

Include sorting examples in `@Operation` description:

```java
@Operation(
    summary = "Search entities",
    description = "... Supports dynamic sorting via ?sort=field,direction "
        + "(e.g., ?sort=authAttemptId,asc). Default sort is by creation date descending.")
```

Document sortable fields in the method javadoc:

```java
/**
 * <p><b>Pagination and Sorting:</b>
 * <ul>
 *   <li>Use <code>?page=0&size=20</code> for pagination
 *   <li>Use <code>?sort=field,direction</code> for sorting
 *   <li>Sortable fields: field1, field2, field3
 * </ul>
 */
```

## Testing

### Unit Tests

Test pagination and sorting behavior:

```java
@Test
void search_ShouldSupportDynamicSorting() {
    Pageable pageable = PageRequest.of(0, 20, 
        Sort.by(Sort.Direction.ASC, "authAttemptId"));
    
    // ... test implementation
}
```

### Postman Collections

Update Postman collections to include:
- `sort` parameter in query parameters (disabled by default)
- Examples in description showing sorting usage
- Test scripts that validate pagination structure

## Migration Checklist

When migrating existing endpoints:

1. ✅ Replace `int page, int size` with `Pageable pageable` in controller
2. ✅ Add `@PageableDefault` with appropriate defaults
3. ✅ Add `@ParameterObject` for Swagger documentation
4. ✅ Remove manual `PageRequest.of()` construction
5. ✅ Remove manual size validation (or move to custom validator if needed)
6. ✅ Update service to use `Specification` if not already using it
7. ✅ Ensure repository extends `JpaSpecificationExecutor`
8. ✅ Update default sort logic in `Specification` (only if unsorted)
9. ✅ Update unit tests to use `Pageable`
10. ✅ Add tests for dynamic sorting
11. ✅ Update Postman collections with `sort` parameter
12. ✅ Update Swagger documentation

## Examples in Codebase

**Reference Implementations**:
- `AuthAttemptController.search()` - Standardized pagination with filters
- `AuditLogController.getAuditLogs()` - Standardized pagination with filters
- `AuthAttemptService.findByFilters()` - Specification-based service
- `AuditLogService.findByFilters()` - Specification-based service

## Best Practices Summary

1. **Controllers**: Use `Pageable` with `@PageableDefault` and `@ParameterObject`
2. **Services**: Use `Specification` with default sort only if unsorted
3. **Repositories**: Extend `JpaSpecificationExecutor` for dynamic queries
4. **Documentation**: Document sortable fields and provide examples
5. **Testing**: Test pagination, sorting, and default values
6. **Postman**: Include `sort` parameter in collections

## Future Considerations

- If pagination defaults need to be configurable per environment, consider externalizing via `@ConfigurationProperties`
- For very large datasets, consider cursor-based pagination as an alternative
- Monitor performance with different page sizes and adjust defaults if needed






