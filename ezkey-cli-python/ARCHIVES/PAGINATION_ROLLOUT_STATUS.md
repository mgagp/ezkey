# Pagination Rollout Status

This document tracks the implementation of pagination across all CLI list commands.

**Date:** 2026-01-30
**Pattern Template:** See `PAGINATION_USAGE_GUIDE.md`
**Implementation Reference:** `ezkey_cli/commands/admin.py` - `list_enrollments()` function

---

## Implementation Status

### ✅ COMPLETED

#### 1. `ezkey admin enrollment list`
- **Backend Controller:** EnrollmentController
- **Pageable:** Yes
- **CLI Status:** Fully implemented with all pagination options
- **Sortable Fields:**
  - enrollmentId
  - enrollmentName
  - createdAt (default)
  - integrationId
  - status

**Implementation Details:**
- Command location: [admin.py:192](admin.py#L192)
- Options: `--page`, `--size`, `--sort`, `--summary`
- Filters: `--integration-id`
- Help text: Comprehensive with examples

---

### ⏳ PENDING (Ready for Implementation)

#### 2. `ezkey admin audit-log list`
- **Backend Controller:** AuditLogController
- **Pageable:** Yes
- **Current Status:** Basic implementation without pagination
- **Command location:** [admin.py:673](admin.py#L673)
- **Sortable Fields:**
  - auditLogId
  - createdAt (default)
  - eventType
  - eventStatus
  - apiName

**Implementation Plan:**
- Add `--page`, `--size`, `--sort`, `--summary` options
- Current filters: `--event-type`, `--event-status`, `--api-name`, `--enrollment-id`, `--admin-id`
- Use existing `pagination_utils` functions
- Follow `list_enrollments()` pattern

---

#### 3. `ezkey admin auth-attempt list`
- **Backend Controller:** AuthAttemptController
- **Pageable:** Yes
- **Current Status:** Basic implementation without pagination
- **Command location:** [admin.py:472](admin.py#L472)
- **Sortable Fields:**
  - authAttemptId
  - createdAt (default)
  - expiresAt
  - enrollmentId

**Implementation Plan:**
- Add `--page`, `--size`, `--sort`, `--summary` options
- Current filters: `--enrollment-id`
- Use existing `pagination_utils` functions
- Follow `list_enrollments()` pattern

---

#### 4. `ezkey admin integration list`
- **Backend Controller:** IntegrationController
- **Pageable:** Yes
- **Current Status:** Basic implementation without pagination
- **Command location:** [admin.py:42](admin.py#L42)
- **Sortable Fields:**
  - id
  - createdAt (default)
  - active

**Implementation Plan:**
- Add `--page`, `--size`, `--sort`, `--summary` options
- No current filters
- Use existing `pagination_utils` functions
- Follow `list_enrollments()` pattern

---

#### 5. `ezkey admin provisioning list` (Admin provisioning)
- **Backend Controller:** AdminProvisioningController
- **Pageable:** Yes
- **Current Status:** No CLI command exists yet
- **Sortable Fields:**
  - id
  - createdAt (default)

**Implementation Plan:**
- Create new command group `provisioning` with `list` subcommand
- Add `--page`, `--size`, `--sort`, `--summary` options
- No filters expected
- Use existing `pagination_utils` functions
- Follow `list_enrollments()` pattern

---

## Implementation Checklist

For each command, follow this checklist:

### Code Changes
- [ ] Add Click options: `--page`, `--size`, `--sort`, `--summary`
- [ ] Import pagination utilities: `build_pagination_params()`, `validate_pagination_options()`, `display_page_summary()`
- [ ] Update function signature to accept pagination parameters
- [ ] Build API query params using `build_pagination_params()`
- [ ] Validate options using `validate_pagination_options()`
- [ ] Display summary when `--summary` flag is set
- [ ] Update docstring with help examples

### Help Text Requirements
- [ ] Clear description of the command
- [ ] Explain pagination (page numbering starts at 0)
- [ ] List available sortable fields with descriptions
- [ ] Provide 2-3 practical examples
- [ ] Explain `--summary` output
- [ ] Keep total length similar to `enrollment list` (not too verbose)

### Testing
- [ ] Test help display: `ezkey admin <resource> list --help`
- [ ] Test default pagination: `ezkey admin <resource> list`
- [ ] Test with `--summary`: `ezkey admin <resource> list --summary`
- [ ] Test page navigation: `--page 0`, `--page 1`, etc.
- [ ] Test sorting: `--sort field,asc` and `--sort field,desc`
- [ ] Test combined options
- [ ] Verify no regressions in existing filters

---

## Priority Order

1. **HIGH:** `integration list` - Most commonly used after enrollment
2. **HIGH:** `audit-log list` - Important for compliance
3. **MEDIUM:** `auth-attempt list` - Useful for security analysis
4. **MEDIUM:** `provisioning list` - Admin management feature

---

## Template to Copy

Use this template when implementing each command:

```python
@command_group.command('list')
@click.option('--filter-param', type=str, default=None, help='Filter description')
@click.option('--page', type=int, default=0, help='Page number (0-based, default: 0)')
@click.option('--size', type=int, default=20, help='Results per page (default: 20)')
@click.option('--sort', type=str, default='createdAt,desc',
              help='Sort by field (field,asc|desc, default: createdAt,desc)')
@click.option('--summary', is_flag=True, help='Show pagination metadata')
@click.pass_context
def list_resource(ctx, filter_param, page, size, sort, summary):
    """
    List all resources with pagination support.

    Pagination: Resources are returned in pages. Use --page to navigate.
    Page numbers start at 0.

    Sortable fields:
      field1          - Description
      field2          - Description (default sort field)

    Examples:
      # First page (default)
      $ ezkey admin resource list

      # With pagination info
      $ ezkey admin resource list --summary

      # Second page, 10 per page, sorted by name
      $ ezkey admin resource list --page 1 --size 10 --sort name,asc

      # Sort by date, show summary
      $ ezkey admin resource list --sort createdAt,asc --summary
    """
    config = ConfigManager()
    client = HttpClient(config)

    try:
        validate_pagination_options(page, size, sort, 'ResourceEntity')

        query_params = build_pagination_params(page, size, sort)
        if filter_param:
            query_params['filterName'] = filter_param

        response = client.get('/api/v1/resources', params=query_params)

        if summary:
            display_page_summary(response)

        JsonUtils.print_formatted(response['content'])

    except ValueError as e:
        click.secho(f"Error: {str(e)}", fg='red')
        ctx.exit(1)
    except Exception as e:
        click.secho(f"Failed to list resources: {str(e)}", fg='red')
        ctx.exit(1)
```

---

## Utility Functions Reference

Located in: `ezkey_cli/utils/pagination_utils.py`

```python
def build_pagination_params(page: int, size: int, sort: str) -> Dict[str, Any]
def validate_pagination_options(page: int, size: int, sort: str, entity_type: str) -> None
def display_page_summary(response: Dict[str, Any]) -> None
```

All functions include comprehensive docstrings and error handling.

---

## Notes

- All sortable fields are configured in `SORTABLE_FIELDS` dict in `pagination_utils.py`
- Default sort direction across all endpoints: `createdAt,desc` (newest first)
- Default page size: 20 items
- Pagination parameters are Spring Data compatible (Pageable)
- Invalid sort fields are caught by validation and show helpful error messages
