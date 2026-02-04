# Pagination and Sorting Usage Guide - Ezkey CLI

This file is in UTF-8 without BOM.

## Overview

Pagination and sorting have been implemented for all `list` commands that query data from the server. This guide helps you understand and test these features.

---

## 1. Basic Concepts

### What is Pagination?

Pagination divides a long list of results into smaller, manageable **pages**.

**Example:**
- You have 187 enrollments total
- With a page size of 20, this creates 10 pages:
  - Page 0: items 1-20
  - Page 1: items 21-40
  - ...
  - Page 9: items 181-187

### Pagination Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `--page` | 0 | Page number (0-based, so page 0 = first page) |
| `--size` | 20 | Number of results per page |
| `--sort` | createdAt,desc | Result ordering (field,direction) |
| `--summary` | False | Display pagination metadata |

---

## 2. Commands with Pagination

The following commands support pagination:

### ✅ Implemented (Production-Ready)
- `ezkey admin enrollment list`

### ⏳ To Be Implemented Soon
- `ezkey admin auth-attempt list`
- `ezkey admin audit-log list`
- `ezkey admin integration list`
- `ezkey admin provisioning list`

---

## 3. Practical Use Cases

### Use Case 1: List all enrollments (default)

```bash
ezkey admin enrollment list
```

**Result:**
- Displays first page (page 0)
- 20 results
- Sorted by creation date descending (newest first)
- Raw JSON without pagination summary

---

### Use Case 2: Navigate between pages

```bash
# Display second page (10 results per page)
ezkey admin enrollment list --page 1 --size 10

# Display 5th page with 50 results per page
ezkey admin enrollment list --page 4 --size 50
```

**Tip:** Pages are numbered starting from 0, so:
- Page 0 = first page
- Page 1 = second page
- etc.

---

### Use Case 3: Sort results

**Sort by name (A→Z):**
```bash
ezkey admin enrollment list --sort enrollmentName,asc
```

**Sort by date (old→recent):**
```bash
ezkey admin enrollment list --sort createdAt,asc
```

**Sort by date descending (default, recent→old):**
```bash
ezkey admin enrollment list --sort createdAt,desc
```

---

### Use Case 4: Display pagination summary

```bash
ezkey admin enrollment list --summary
```

**Result:**
```
═══ Pagination Summary ═══
Page:     1/10 (showing 20 items)
Total:    187 items
Per page: 20

← Previous: --page 0
→ Next:     --page 2
═══════════════════════════

[JSON data...]
```

**Benefits:**
- See total number of pages
- See total number of items
- Commands to navigate to previous/next page

---

### Use Case 5: Combine filtering, pagination, and sorting

```bash
# Filter by integration, 2nd page, 10 per page, sorted by name
ezkey admin enrollment list \
  --integration-id 5 \
  --page 1 \
  --size 10 \
  --sort enrollmentName,asc \
  --summary
```

---

## 4. Sortable Fields by Command

### enrollment list
```
enrollmentId          - Unique enrollment ID
enrollmentName        - Enrollment name
createdAt            - Creation date
integrationId        - Associated integration ID
status               - Status (CREATED, BOUND, VERIFIED, INVALID)
```

**Sort directions:**
- `asc` - Ascending (A→Z, 0→9, old→recent)
- `desc` - Descending (Z→A, 9→0, recent→old)

---

## 5. Complete Test Scenarios

### Test 1: Explore the data

```bash
# 1. See how many enrollments you have
ezkey admin enrollment list --summary

# Result: check "Total: X items"
```

### Test 2: Browse page by page

```bash
# 1. First page
ezkey admin enrollment list --page 0 --summary

# 2. Second page
ezkey admin enrollment list --page 1 --summary

# 3. Last page (calculate from total)
# If total=187, pages of 20 = 10 pages, so pages 0-9
ezkey admin enrollment list --page 9 --summary
```

### Test 3: Sort differently

```bash
# By default (recent→old date)
ezkey admin enrollment list --sort createdAt,desc --page 0 --size 5

# By name alphabetically
ezkey admin enrollment list --sort enrollmentName,asc --page 0 --size 5

# By ID (old→recent)
ezkey admin enrollment list --sort enrollmentId,asc --page 0 --size 5
```

### Test 4: Optimize for large volumes

```bash
# Instead of paginating manually, increase size
ezkey admin enrollment list --size 100 --summary

# This retrieves 100 results per page, fewer total requests
```

---

## 6. Understanding the JSON Response

When you run a command with pagination, you receive a **Spring Boot Page** object:

```json
{
  "content": [
    { "id": 1, "name": "Enrollment A", "status": "BOUND", ... },
    { "id": 2, "name": "Enrollment B", "status": "BOUND", ... }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 187,
    "totalPages": 10
  }
}
```

### Key fields:
- `content[]` - Current results from this page
- `page.size` - Number of results per page (as requested)
- `page.number` - Current page number (0-based)
- `page.totalElements` - TOTAL number of results across all pages
- `page.totalPages` - TOTAL number of pages

---

## 7. Useful Commands for Testing

### Get the total number of results

```bash
# Just display the summary
ezkey admin enrollment list --summary

# Look for "Total: X items"
```

### Calculate how many pages exist

```bash
# Use --summary which shows "Page: X/Y"
ezkey admin enrollment list --summary

# If it says "Page: 1/10" → there are 10 pages
```

### Test the boundaries

```bash
# Try extremely high page number (should be empty or error)
ezkey admin enrollment list --page 999

# Try very large page size
ezkey admin enrollment list --size 500
```

### Validate a sort

```bash
# Get 5 results sorted by name
ezkey admin enrollment list --sort enrollmentName,asc --size 5

# Manually verify that names are in A→Z order
```

---

## 8. Troubleshooting

### Error: "Invalid sort field"

**Problem:** The sort field doesn't exist
```bash
# ❌ WRONG
ezkey admin enrollment list --sort invalidField,asc

# ✅ CORRECT
ezkey admin enrollment list --sort enrollmentName,asc
```

**Solution:** Use a valid field from the list above.

---

### Error: "Invalid sort direction"

**Problem:** Invalid sort direction
```bash
# ❌ WRONG
ezkey admin enrollment list --sort enrollmentName,ascending

# ✅ CORRECT
ezkey admin enrollment list --sort enrollmentName,asc
```

**Solution:** Use only `asc` or `desc`.

---

### Empty results with high --page number

**Problem:** You requested a page that doesn't exist
```bash
# You have 187 items (10 pages with size=20)
# But you request page 999
ezkey admin enrollment list --page 999

# Result: empty page
```

**Solution:** Use `--summary` to see how many pages exist.

---

### Token expired (HTTP 401)

**Problem:** Your authentication token has expired
```bash
# ✗ Error: HTTP 401:
```

**Solution:** Re-authenticate
```bash
ezkey admin auth login --username admin.docker
```

---

## 9. Next Steps

### Upcoming Commands to Test

Once comfortable with `enrollment list`, test future implementations:

1. **auth-attempt list** - Track authentication attempts
   ```bash
   ezkey admin auth-attempt list --status PENDING --sort createdAt,desc --summary
   ```

2. **audit-log list** - Audit logs for compliance
   ```bash
   ezkey admin audit-log list --event-type ENROLLMENT_CREATE --summary
   ```

3. **integration list** - List integrations
   ```bash
   ezkey admin integration list --sort createdAt,desc --summary
   ```

---

## 10. Essential Commands Summary

```bash
# Display results
ezkey admin enrollment list

# With pagination metadata
ezkey admin enrollment list --summary

# Second page
ezkey admin enrollment list --page 1

# Sort by name
ezkey admin enrollment list --sort enrollmentName,asc

# Combine: 2nd page, 10 per page, sort by name
ezkey admin enrollment list --page 1 --size 10 --sort enrollmentName,asc --summary

# Filter by specific integration
ezkey admin enrollment list --integration-id 5 --summary
```

---

## Feedback Welcome!

This is your interface. If any commands are unclear or if you need additional features, don't hesitate to ask!

---

**Date:** 2026-01-30
**Version:** 1.0
**Status:** Complete pagination CLI guide for Ezkey
