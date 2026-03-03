# Ezkey CLI Python — Revision Report

**Date:** 2026-03-03  
**Scope:** Command line (parameters), display/pagination, integration management

---

## 1. Command Line (Parameters)

### Status: ✅ Aligned

The CLI uses **Click** for argument parsing. Integration commands expose:

| Command | Parameters | Notes |
|---------|------------|-------|
| `ezkey admin integration list` | `--integration-name`, `--active`, `--created-after`, `--created-before`, `--tenant-id`, `--page`, `--size`, `--sort`, `--summary` | Full filter + pagination support |
| `ezkey admin integration get` | `--id` | ✅ |
| `ezkey admin integration create` | `--code`, `--name`, `--description`, `--data` | ✅ |
| `ezkey admin integration delete` | `--id` + confirmation | ✅ |

**Other list commands** (enrollments, auth-attempts, audit-logs, provisioning) also expose `--page`, `--size`, `--sort`, `--summary` and relevant filters.

### Change applied
- **Device enroll**: Removed `--language` option (no longer used by Auth API bind endpoint; aligned with demo-device removal).

---

## 2. Display & Pagination

### Pagination format support

The Admin API uses `PageSerializationMode.VIA_DTO`, which returns:

```json
{
  "content": [...],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 187,
    "totalPages": 10
  }
}
```

### Change applied
- **`pagination_utils.display_page_summary()`**: Now supports both:
  - **Flat format** (DIRECT): `totalElements`, `totalPages`, `number`, `size` at top level
  - **Nested format** (VIA_DTO): same fields inside `page` object

The CLI `--summary` flag now correctly displays pagination metadata for Admin API responses.

### TUI pagination

TUI screens (Integrations, Enrollments, API Keys, Tenants, Audit Logs) already use `response.get("page", {})` and extract from the nested `page` object. ✅ No change needed.

---

## 3. Integration Management

### CLI coverage

| Endpoint | CLI command | Status |
|----------|-------------|--------|
| `GET /api/v1/integrations` | `ezkey admin integration list` | ✅ |
| `GET /api/v1/integrations/{id}` | `ezkey admin integration get --id` | ✅ |
| `POST /api/v1/integrations` | `ezkey admin integration create` | ✅ |
| `DELETE /api/v1/integrations/{id}` | `ezkey admin integration delete --id` | ✅ |

### TUI coverage

| Feature | Status |
|---------|--------|
| List integrations (paginated) | ✅ |
| Filter by name, tenant ID, active | ✅ |
| Create integration | ✅ |
| View integration detail | ✅ |
| Delete integration | ✅ |

### Endpoints not exposed in CLI/TUI

| Endpoint | Purpose |
|----------|---------|
| `POST /api/v1/integrations/{id}/enrollments/revoke-all` | Bulk revoke all enrollments |
| `POST /api/v1/integrations/{id}/enrollments/deactivate-all` | Bulk deactivate all enrollments |
| `POST /api/v1/integrations/{id}/enrollments/reactivate-all` | Bulk reactivate all enrollments |

These are available in the Postman collection; adding them to the TUI Integration Detail screen would be a future enhancement.

---

## 4. Summary

| Area | Status | Changes |
|------|--------|---------|
| CLI parameters | ✅ | Removed `--language` from `device enroll` |
| Pagination display | ✅ | `display_page_summary` supports VIA_DTO format |
| Integration management | ✅ | Complete for list/get/create/delete; revoke/deactivate/reactivate-all not exposed |

---

## 5. Files Modified

- `ezkey_cli/utils/pagination_utils.py` — Support nested `page` format in `display_page_summary`
- `ezkey_cli/commands/device.py` — Remove `--language` from `enroll` command
