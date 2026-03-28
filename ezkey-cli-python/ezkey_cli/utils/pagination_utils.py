"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Pagination Utilities
Description: Utilities for handling paginated API responses with Spring Boot Page format
"""

from typing import Any, Dict, Optional

import click
from colorama import Fore, Style


# Sortable fields for each entity type (aligned with Spring Data repository definitions)
SORTABLE_FIELDS = {
    'audit-log': ['auditLogId', 'createdAt', 'eventType', 'eventStatus', 'apiName'],
    'auth-attempt': ['authAttemptId', 'createdAt', 'expiresAt', 'enrollmentId'],
    'enrollment': ['enrollmentId', 'enrollmentName', 'createdAt', 'integrationId', 'status'],
    'integration': ['id', 'createdAt', 'active'],
    'admin': ['id', 'createdAt', 'username', 'role'],  # To be confirmed with controller
    'reencryption-batch': [
        'batchId',
        'status',
        'targetTable',
        'targetColumn',
        'createdAt',
        'startedAt',
        'completedAt',
        'progressPct',
        'recordsTotal',
        'recordsDone',
        'recordsFailed',
        'oldKey.keyId',
        'newKey.keyId',
    ],
}


def build_pagination_params(
    page: Optional[int] = None,
    size: Optional[int] = None,
    sort: Optional[str] = None
) -> Dict[str, Any]:
    """
    Build query parameters for Spring Boot Pageable endpoints.

    Constructs a dictionary of pagination parameters that can be passed to HTTP GET requests.
    Only includes parameters that are explicitly set (non-None values).

    Args:
        page: Page number (0-based). If None, uses server default (0)
        size: Page size (number of results per page). If None, uses server default (20)
        sort: Sort criteria in format "field,direction" (e.g., "createdAt,desc")
              Direction must be "asc" or "desc"

    Returns:
        Dictionary of query parameters for the HTTP request

    Raises:
        ValueError: If sort format is invalid or direction is not asc/desc

    Examples:
        >>> build_pagination_params(page=1, size=50)
        {'page': 1, 'size': 50}

        >>> build_pagination_params(sort='createdAt,desc')
        {'sort': 'createdAt,desc'}

        >>> build_pagination_params(page=0, size=10, sort='enrollmentName,asc')
        {'page': 0, 'size': 10, 'sort': 'enrollmentName,asc'}
    """
    params = {}

    if page is not None:
        params['page'] = page

    if size is not None:
        params['size'] = size

    if sort:
        # Validate sort format
        if ',' not in sort:
            raise ValueError(
                "Sort format must be: field,direction (e.g., 'createdAt,desc'). "
                f"Got: '{sort}'"
            )

        field, direction = sort.split(',', 1)
        direction = direction.lower().strip()

        if direction not in ['asc', 'desc']:
            raise ValueError(
                f"Sort direction must be 'asc' or 'desc'. Got: '{direction}'"
            )

        # Spring Boot expects the exact format "field,direction"
        params['sort'] = f"{field.strip()},{direction}"

    return params


def display_page_summary(response_data: Any, verbose: bool = False) -> None:
    """
    Display a formatted summary of Spring Boot Page metadata.

    Extracts pagination metadata from a Spring Boot Page response and displays
    a user-friendly summary including current page, total pages, total items,
    and navigation hints.

    Args:
        response_data: The decoded JSON response (Spring Boot Page object)
        verbose: Whether to display additional debug information

    Expected response_data structure (supports both formats):

    Flat format (DIRECT):
        {"content": [...], "totalPages": 10, "totalElements": 187, "number": 0, "size": 20, ...}

    Nested format (VIA_DTO - Admin API default):
        {"content": [...], "page": {"size": 20, "number": 0, "totalElements": 187, "totalPages": 5}}

    Examples:
        >>> data = {"number": 2, "totalPages": 10, "totalElements": 187,
        ...         "size": 20, "numberOfElements": 20, "first": False, "last": False}
        >>> display_page_summary(data)
        ═══ Pagination Summary ═══
        Page:     3/10 (showing 20 items)
        Total:    187 items
        Per page: 20
        ← Previous: --page 1
        → Next:     --page 3
        ═══════════════════════════
    """
    if not isinstance(response_data, dict):
        if verbose:
            click.echo(
                f"{Fore.YELLOW}[VERBOSE] Response is not a Page object, skipping pagination summary{Style.RESET_ALL}",
                err=True
            )
        return

    # Support both flat format (DIRECT) and nested format (VIA_DTO - Admin API default)
    page_obj = response_data.get('page', {}) if isinstance(response_data.get('page'), dict) else {}

    def _get(key: str, default: Any = 0) -> Any:
        val = response_data.get(key)
        if val is not None:
            return val
        return page_obj.get(key, default)

    total_elements = _get('totalElements', 0)
    total_pages = _get('totalPages', 0)
    current_page = _get('number', 0)
    page_size = _get('size', 20)
    number_of_elements = _get('numberOfElements', 0)
    if number_of_elements == 0 and 'content' in response_data:
        number_of_elements = len(response_data.get('content', []))
    is_first = response_data.get('first')
    is_last = response_data.get('last')
    is_empty = response_data.get('empty', False)
    if is_first is None and is_last is None and total_pages > 0:
        is_first = current_page == 0
        is_last = current_page >= total_pages - 1
    elif is_first is None:
        is_first = current_page == 0
    elif is_last is None:
        is_last = current_page >= total_pages - 1

    if verbose:
        click.echo(
            f"{Fore.CYAN}[VERBOSE] Page metadata extracted: "
            f"page={current_page}, totalPages={total_pages}, "
            f"totalElements={total_elements}, size={page_size}{Style.RESET_ALL}",
            err=True
        )

    # Display formatted summary
    click.echo(f"\n{Fore.CYAN}═══ Pagination Summary ═══{Style.RESET_ALL}")

    if is_empty:
        click.echo(f"{Fore.YELLOW}No results found{Style.RESET_ALL}")
    else:
        # Human-readable page number (1-based for display)
        display_page = current_page + 1
        click.echo(f"Page:     {display_page}/{total_pages} (showing {number_of_elements} items)")
        click.echo(f"Total:    {total_elements} items")
        click.echo(f"Per page: {page_size}")

        # Navigation hints
        if not is_first and not is_last:
            click.echo(f"\n{Fore.YELLOW}← Previous: --page {current_page - 1}{Style.RESET_ALL}")
            click.echo(f"{Fore.YELLOW}→ Next:     --page {current_page + 1}{Style.RESET_ALL}")
        elif not is_first:
            click.echo(f"\n{Fore.YELLOW}← Previous: --page {current_page - 1}{Style.RESET_ALL}")
            click.echo(f"{Fore.GREEN}✓ Last page reached{Style.RESET_ALL}")
        elif not is_last:
            click.echo(f"\n{Fore.YELLOW}→ Next:     --page {current_page + 1}{Style.RESET_ALL}")
        else:
            click.echo(f"\n{Fore.GREEN}✓ All results on single page{Style.RESET_ALL}")

    click.echo(f"{Fore.CYAN}═══════════════════════════{Style.RESET_ALL}\n")


def validate_sort_field(entity_type: str, sort_string: Optional[str]) -> None:
    """
    Validate that the sort field is supported by the controller.

    Checks if the specified sort field is in the list of sortable fields
    for the given entity type. Provides helpful error messages with valid
    field names if validation fails.

    Args:
        entity_type: Type of entity (e.g., 'enrollment', 'auth-attempt')
                     Must match a key in SORTABLE_FIELDS dictionary
        sort_string: Sort criteria in format "field,direction"
                     If None or empty, validation passes (no sort specified)

    Raises:
        ValueError: If the sort field is not in the list of valid fields
                    for the given entity type

    Examples:
        >>> validate_sort_field('enrollment', 'enrollmentName,asc')
        # Passes silently

        >>> validate_sort_field('enrollment', 'invalidField,desc')
        ValueError: Invalid sort field 'invalidField' for enrollment.
                    Valid fields: enrollmentId, enrollmentName, createdAt, integrationId, status

        >>> validate_sort_field('enrollment', None)
        # Passes silently (no sort specified)
    """
    if not sort_string:
        return

    # Extract field name from "field,direction" format
    field = sort_string.split(',')[0].strip()

    # Get valid fields for this entity type
    valid_fields = SORTABLE_FIELDS.get(entity_type, [])

    if not valid_fields:
        raise ValueError(
            f"Unknown entity type '{entity_type}'. "
            f"Valid types: {', '.join(SORTABLE_FIELDS.keys())}"
        )

    if field not in valid_fields:
        raise ValueError(
            f"Invalid sort field '{field}' for {entity_type}. "
            f"Valid fields: {', '.join(valid_fields)}"
        )


def validate_pagination_options(
    page: Optional[int],
    size: Optional[int],
    sort: Optional[str],
    entity_type: str
) -> None:
    """
    Validate all pagination options before making the API call.

    Performs comprehensive validation of pagination parameters:
    - Page number must be >= 0
    - Size must be between 1 and 100 (warns if > 100)
    - Sort field must be valid for the entity type
    - Sort format must be "field,direction"

    Args:
        page: Page number (0-based)
        size: Page size
        sort: Sort criteria in format "field,direction"
        entity_type: Type of entity for sort field validation

    Raises:
        click.BadParameter: If any validation fails, with descriptive message

    Examples:
        >>> validate_pagination_options(0, 20, 'createdAt,desc', 'enrollment')
        # Passes all validations

        >>> validate_pagination_options(-1, 20, None, 'enrollment')
        click.BadParameter: Page number must be >= 0

        >>> validate_pagination_options(0, 0, None, 'enrollment')
        click.BadParameter: Page size must be >= 1
    """
    # Validate page number
    if page is not None and page < 0:
        raise click.BadParameter("Page number must be >= 0 (pages are 0-based)")

    # Validate page size
    if size is not None:
        if size < 1:
            raise click.BadParameter("Page size must be >= 1")
        if size > 100:
            # Warning only, not an error
            from .output_utils import OutputUtils
            OutputUtils.warning(
                f"Large page size ({size}) may impact performance. "
                "Consider using smaller pages with --page navigation."
            )

    # Validate sort field
    if sort:
        try:
            validate_sort_field(entity_type, sort)
        except ValueError as e:
            raise click.BadParameter(str(e))


def extract_page_content(response_data: Any) -> Any:
    """
    Extract the content array from a Spring Boot Page response.

    Safely extracts the "content" field from a paginated response.
    If the response is not a Page object or doesn't have a content field,
    returns the original data unchanged.

    Args:
        response_data: The decoded JSON response (potentially a Page object)

    Returns:
        The content array if present, otherwise the original response_data

    Examples:
        >>> data = {"content": [{"id": 1}, {"id": 2}], "totalElements": 2}
        >>> extract_page_content(data)
        [{"id": 1}, {"id": 2}]

        >>> data = [{"id": 1}, {"id": 2}]
        >>> extract_page_content(data)
        [{"id": 1}, {"id": 2}]
    """
    if isinstance(response_data, dict) and 'content' in response_data:
        return response_data['content']
    return response_data


def is_paginated_response(response_data: Any) -> bool:
    """
    Check if the response is a Spring Boot Page object.

    Determines whether the response data follows the Spring Boot Page structure
    by checking for key pagination fields.

    Args:
        response_data: The decoded JSON response

    Returns:
        True if the response appears to be a Spring Boot Page, False otherwise

    Examples:
        >>> data = {"content": [], "totalPages": 1, "number": 0}
        >>> is_paginated_response(data)
        True

        >>> data = [{"id": 1}, {"id": 2}]
        >>> is_paginated_response(data)
        False
    """
    if not isinstance(response_data, dict):
        return False

    # Check for key Page fields
    required_fields = ['content', 'totalPages', 'number']
    return all(field in response_data for field in required_fields)
