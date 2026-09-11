"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Admin Command
Description: Admin API commands for integrations, enrollments, and auth attempts
"""

from typing import Any, Dict
from pathlib import Path

import click

from ..config import ConfigManager
from ..utils import (
    HttpClient,
    JsonUtils,
    OutputUtils,
    build_pagination_params,
    display_page_summary,
    validate_pagination_options,
)


@click.group(name='admin')
@click.pass_context
def admin_group(ctx):
    """Admin API commands for managing integrations, enrollments, and auth attempts."""
    pass


# Integration commands
@admin_group.group('integration')
@click.pass_context
def integration_group(ctx):
    """Integration management commands."""
    pass


@integration_group.command('list')
@click.option('--integration-name', type=str, help='Filter by integration name')
@click.option('--active', type=str, help='Filter by active flag (true or false)')
@click.option('--created-after', type=str,
              help='Filter by created timestamp (ISO-8601, e.g., 2025-01-31T12:00:00Z)')
@click.option('--created-before', type=str,
              help='Filter by created timestamp (ISO-8601, e.g., 2025-01-31T12:00:00Z)')
@click.option('--tenant-id', type=int, help='Filter by tenant ID (GlobalAdmin only)')
@click.option('--page', type=int, default=0, help='Page number (0-based, default: 0)')
@click.option('--size', type=int, default=20, help='Results per page (default: 20)')
@click.option('--sort', type=str, default='createdAt,desc',
              help='Sort by field (field,asc|desc, default: createdAt,desc)')
@click.option('--summary', is_flag=True, help='Show pagination metadata')
@click.pass_context
def list_integrations(
    ctx,
    integration_name,
    active,
    created_after,
    created_before,
    tenant_id,
    page,
    size,
    sort,
    summary
):
    """
    List all integrations with pagination support.

    Integrations represent applications or systems protected by Ezkey MFA.
    Each integration has its own cryptographic keys and enrollments.

    Pagination: Results are returned in pages. Use --page to navigate.
    Page numbers start at 0.

    Filters:
    - Use --integration-name to filter by name
    - Use --active to filter by active flag
    - Use --created-after / --created-before for created timestamp range
    - Use --tenant-id to filter by tenant ID (GlobalAdmin only; ignored for TenantAdmin)

    Sortable fields:
      id              - Integration ID
      createdAt       - Creation date (default sort field)
      active          - Active status

    Examples:
      # First page (default)
      $ ezkey admin integration list

      # With pagination info
      $ ezkey admin integration list --summary

      # Second page, 10 per page, sorted by name
      $ ezkey admin integration list --page 1 --size 10 --sort id,asc

      # Sort by creation date ascending, show summary
      $ ezkey admin integration list --sort createdAt,asc --summary
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    try:
        validate_pagination_options(page, size, sort, 'integration')

        query_params = build_pagination_params(page, size, sort)
        if integration_name:
            query_params['integrationName'] = integration_name
        if active is not None:
            active_value = str(active).strip().lower()
            if active_value in ['true', 'false']:
                query_params['active'] = active_value == 'true'
            else:
                OutputUtils.error("--active must be 'true' or 'false'")
                ctx.exit(1)
        if created_after:
            query_params['createdAfter'] = created_after
        if created_before:
            query_params['createdBefore'] = created_before
        if tenant_id is not None:
            query_params['tenantId'] = tenant_id

        url = f"{admin_url}/api/v1/integrations"
        OutputUtils.verbose(f"GET {url}", verbose)
        OutputUtils.verbose(f"Params: {query_params}", verbose)

        response = http_client.get(url, params=query_params)

        if summary and response.success and response.data:
            display_page_summary(response.data, verbose=verbose)

        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)

    except ValueError as e:
        OutputUtils.error(str(e))
        ctx.exit(1)
    except Exception as e:
        OutputUtils.error(f"Failed to list integrations: {str(e)}")
        ctx.exit(1)


@integration_group.command('get')
@click.option('--id', required=True, type=int, help='Integration ID')
@click.pass_context
def get_integration(ctx, id):
    """
    Get detailed information about a specific integration.

    Returns integration details including name, description, and active status.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/integrations/{id}"
    OutputUtils.verbose(f"GET {url}", verbose)

    response = http_client.get(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@integration_group.command('create')
@click.option('--code', type=str, required=True,
              help='Unique code for the integration (alphanumeric, hyphens, underscores). Example: web-portal, mobile-app')
@click.option('--name', type=str, help='Integration display name')
@click.option('--description', type=str, help='Integration description (optional)')
@click.option('--data', help='JSON data (or @filename for file input). Overrides: code, name, description')
@click.pass_context
def create_integration(ctx, code, name, description, data):
    """
    Create a new integration for MFA protection.

    An integration represents an application or system that will be protected
    by Ezkey MFA. Each integration has its own cryptographic keys and can have
    multiple enrollments (users/devices).

    Example JSON (--data):
      {"code": "my-app", "name": "My Application", "description": "Optional description"}
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    # Process data input
    if data:
        try:
            json_data = JsonUtils.process_input(data)
        except Exception as e:
            OutputUtils.error(f"Invalid JSON data: {str(e)}")
            return
    else:
        json_data = {}

    # Required code (from option or --data)
    json_data['code'] = json_data.get('code') or code
    json_data['name'] = json_data.get('name') or name or ''
    json_data['description'] = json_data.get('description') if json_data.get('description') is not None else description

    url = f"{admin_url}/api/v1/integrations"
    OutputUtils.verbose(f"POST {url}", verbose)
    if verbose:
        OutputUtils.verbose(f"Data: {JsonUtils.format_output(json_data)}", verbose)

    response = http_client.post(url, json_data=json_data)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@integration_group.command('delete')
@click.option('--id', required=True, type=int, help='Integration ID')
@click.confirmation_option(prompt='Are you sure you want to delete this integration?')
@click.pass_context
def delete_integration(ctx, id):
    """
    Delete an integration and all associated data.

    WARNING: This will delete all enrollments, auth attempts, and API keys
    associated with this integration. This action cannot be undone.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/integrations/{id}"
    OutputUtils.verbose(f"DELETE {url}", verbose)

    response = http_client.delete(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


# Enrollment commands
@admin_group.group('enrollment')
@click.pass_context
def enrollment_group(ctx):
    """Enrollment management commands."""
    pass


@enrollment_group.command('list')
@click.option('--integration-id', type=int, help='Filter by integration ID')
@click.option('--status', type=str,
              help='Filter by status (CREATED, VERIFIED, BOUND, INVALID)')
@click.option('--enrollment-name', type=str, help='Filter by enrollment name')
@click.option('--active', type=str,
              help='Filter by active flag (true or false)')
@click.option('--created-after', type=str,
              help='Filter by created timestamp (ISO-8601, e.g., 2025-01-31T12:00:00Z)')
@click.option('--created-before', type=str,
              help='Filter by created timestamp (ISO-8601, e.g., 2025-01-31T12:00:00Z)')
@click.option('--page', type=int, default=None,
              help='Page number (0-based). Default: 0')
@click.option('--size', type=int, default=None,
              help='Page size (number of results per page). Default: 20')
@click.option('--sort', type=str, default=None,
              help='Sort criteria in format: field,direction (e.g., createdAt,desc). '
                   'Valid fields: enrollmentId, enrollmentName, createdAt, integrationId, status')
@click.option('--summary', is_flag=True, default=False,
              help='Display pagination summary')
@click.pass_context
def list_enrollments(
    ctx,
    integration_id,
    status,
    enrollment_name,
    active,
    created_after,
    created_before,
    page,
    size,
    sort,
    summary
):
    """
    List all enrollments in the system with pagination and sorting support.

    Enrollments represent the association between a user/device and an integration.
    Each enrollment has cryptographic keys for secure authentication.

    Filters:
    - Use --integration-id to filter by specific integration
    - Use --status to filter by enrollment status
    - Use --enrollment-name to filter by name (partial match)
    - Use --active to filter by active flag
    - Use --created-after / --created-before for created timestamp range

    Pagination:
    - Use --page and --size to navigate through results (e.g., --page 1 --size 50)
    - Use --sort to order results (e.g., --sort enrollmentName,asc)
    - Use --summary to display pagination metadata

    Examples:
    \b
        # List first page with default size (20)
        ezkey admin enrollment list

        # List second page with 50 items per page
        ezkey admin enrollment list --page 1 --size 50

        # Sort by enrollment name (ascending)
        ezkey admin enrollment list --sort enrollmentName,asc

        # Filter by integration with pagination summary
        ezkey admin enrollment list --integration-id 5 --summary

        # Combine all options
        ezkey admin enrollment list --integration-id 5 --page 0 --size 10 --sort createdAt,desc --summary
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    # Validate pagination options
    try:
        validate_pagination_options(page, size, sort, 'enrollment')
    except click.BadParameter as e:
        OutputUtils.error(str(e))
        return

    # Build query parameters
    url = f"{admin_url}/api/v1/enrollments"
    params = {}

    # Add filter parameters
    if integration_id:
        params['integrationId'] = integration_id
    if status:
        params['status'] = status
    if enrollment_name:
        params['enrollmentName'] = enrollment_name
    if active is not None:
        active_value = str(active).strip().lower()
        if active_value in ['true', 'false']:
            params['active'] = active_value == 'true'
        else:
            OutputUtils.error("--active must be 'true' or 'false'")
            return
    if created_after:
        params['createdAfter'] = created_after
    if created_before:
        params['createdBefore'] = created_before

    # Add pagination parameters
    try:
        pagination_params = build_pagination_params(page, size, sort)
        params.update(pagination_params)
    except ValueError as e:
        OutputUtils.error(str(e))
        return

    OutputUtils.verbose(f"GET {url}", verbose)
    if params:
        OutputUtils.verbose(f"Params: {params}", verbose)

    # Make API request
    response = http_client.get(url, params=params)

    # Display pagination summary if requested
    if summary and response.success and response.data:
        display_page_summary(response.data, verbose=verbose)

    # Output response
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@enrollment_group.command('get')
@click.option('--id', required=True, type=int, help='Enrollment ID')
@click.pass_context
def get_enrollment(ctx, id):
    """
    Get detailed information about a specific enrollment.

    Returns enrollment details including status, proof token, challenge code,
    device public key, and associated integration information.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/enrollments/{id}"
    OutputUtils.verbose(f"GET {url}", verbose)

    response = http_client.get(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@enrollment_group.command('create')
@click.option('--integration-id', required=True, type=int, help='Integration ID')
@click.option('--name', type=str, help='Enrollment name')
@click.option('--challenge-required', is_flag=True,
              help='Require challenge for auth attempts')
@click.option('--data', help='JSON data (or @filename for file input)')
@click.pass_context
def create_enrollment(ctx, integration_id, name, challenge_required, data):
    """
    Create a new enrollment for device binding.

    This generates enrollment credentials (proof token and challenge code)
    that can be used to bind a device to this integration. The device will
    use these credentials during the enrollment verification process.

        Example JSON:
            {
                "name": "My Device",
                "authAttemptChallengeRequired": true
            }
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    # Process data input
    if data:
        try:
            json_data = JsonUtils.process_input(data)
        except Exception as e:
            OutputUtils.error(f"Invalid JSON data: {str(e)}")
            return
    else:
        json_data = {}

    # Add integration ID
    json_data['integrationId'] = integration_id

    if name:
        json_data['name'] = name

    if challenge_required:
        json_data['authAttemptChallengeRequired'] = True

    if not json_data.get('name'):
        OutputUtils.error("Enrollment name is required. Use --name or provide it in --data.")
        return

    url = f"{admin_url}/api/v1/enrollments"
    OutputUtils.verbose(f"POST {url}", verbose)
    if verbose:
        OutputUtils.verbose(f"Data: {JsonUtils.format_output(json_data)}", verbose)

    response = http_client.post(url, json_data=json_data)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@enrollment_group.command('delete')
@click.option('--id', required=True, type=int, help='Enrollment ID')
@click.confirmation_option(prompt='Are you sure you want to delete this enrollment?')
@click.pass_context
def delete_enrollment(ctx, id):
    """
    Delete an enrollment from the system.

    WARNING: This will permanently remove the enrollment and all associated
    authentication attempts. This action cannot be undone.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/enrollments/{id}"
    OutputUtils.verbose(f"DELETE {url}", verbose)

    response = http_client.delete(url)

    if response.success:
        OutputUtils.success(f"✅ Enrollment {id} deleted successfully")
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@enrollment_group.command('qrcode')
@click.option('--id', required=True, type=int, help='Enrollment ID')
@click.option('--output', '-o', help='Output file path (default: enrollment-{id}.png)')
@click.pass_context
def get_enrollment_qrcode(ctx, id, output):
    """
    Generate QR code for enrollment credentials.

    Returns a PNG QR code image containing enrollment credentials
    (enrollmentId|enrollmentProofToken) that can be scanned by a device
    to bind to this enrollment.

    The QR code is saved to a file. If --output is not specified,
    it defaults to enrollment-{id}.png in the current directory.
    """
    config: ConfigManager = ctx.obj['config']
    verbose = ctx.obj.get('verbose', False)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    # Set up HTTP client with binary response support
    http_client = HttpClient(config)
    # Override Accept header to accept image/png
    original_accept = http_client.session.headers.get('Accept')
    http_client.session.headers['Accept'] = 'image/png,*/*'

    url = f"{admin_url}/api/v1/enrollments/{id}/qrcode"
    OutputUtils.verbose(f"GET {url}", verbose)

    try:
        response = http_client.session.get(url, timeout=http_client.timeout)

        if response.ok:
            # Determine output filename
            if output:
                output_file = output
            else:
                output_file = f"enrollment-{id}.png"

            # Save binary content to file
            with open(output_file, 'wb') as f:
                f.write(response.content)

            OutputUtils.success(f"✅ QR code saved to {output_file}")
            OutputUtils.info(f"File size: {len(response.content)} bytes")
        else:
            # Try to parse error as JSON
            try:
                error_data = response.json()
                error_msg = error_data.get('message', error_data.get('error', 'Unknown error'))
                OutputUtils.error(f"Failed to generate QR code: {error_msg}")
            except ValueError:
                OutputUtils.error(f"Failed to generate QR code: HTTP {response.status_code}")

            if response.status_code == 400:
                OutputUtils.info("💡 Enrollment may be missing proof token")
            elif response.status_code == 404:
                OutputUtils.info("💡 Enrollment not found")
    except Exception as e:
        OutputUtils.error(f"Request failed: {str(e)}")
    finally:
        # Restore original Accept header
        if original_accept:
            http_client.session.headers['Accept'] = original_accept
        else:
            http_client.session.headers.pop('Accept', None)


# Auth attempt commands
@admin_group.group('auth-attempt')
@click.pass_context
def auth_attempt_group(ctx):
    """Authentication attempt management commands."""
    pass


@auth_attempt_group.command('list')
@click.option('--enrollment-id', type=int, help='Filter by enrollment ID')
@click.option('--status', type=str,
              help='Filter by status (PENDING, READ, ACCEPTED, REJECTED, INVALID, EXPIRED)')
@click.option('--integration-id', type=int, help='Filter by integration ID')
@click.option('--created-after', type=str,
              help='Filter by created timestamp (ISO-8601, e.g., 2025-01-31T12:00:00Z)')
@click.option('--created-before', type=str,
              help='Filter by created timestamp (ISO-8601, e.g., 2025-01-31T12:00:00Z)')
@click.option('--page', type=int, default=0, help='Page number (0-based, default: 0)')
@click.option('--size', type=int, default=20, help='Results per page (default: 20)')
@click.option('--sort', type=str, default='createdAt,desc',
              help='Sort by field (field,asc|desc, default: createdAt,desc)')
@click.option('--summary', is_flag=True, help='Show pagination metadata')
@click.pass_context
def list_auth_attempts(
    ctx,
    enrollment_id,
    status,
    integration_id,
    created_after,
    created_before,
    page,
    size,
    sort,
    summary
):
    """
    List all authentication attempts with pagination support.

    Authentication attempts represent MFA requests that devices must approve
    or reject. Each attempt has a unique proof token and tracks its status
    (PENDING, READ, ACCEPTED, REJECTED, INVALID, EXPIRED).

    Filters:
    - Use --enrollment-id to filter by specific enrollment
    - Use --status to filter by status
    - Use --integration-id to filter by integration
    - Use --created-after / --created-before for created timestamp range

    Pagination: Results are returned in pages. Use --page to navigate.
    Page numbers start at 0.

    Sortable fields:
      authAttemptId   - Auth attempt ID
      createdAt       - Creation date (default sort field)
      expiresAt       - Expiration date
      enrollmentId    - Associated enrollment ID

    Examples:
      # First page (default)
      $ ezkey admin auth-attempt list

      # With pagination info
      $ ezkey admin auth-attempt list --summary

      # Filter by enrollment, show summary
      $ ezkey admin auth-attempt list --enrollment-id 5 --summary

    # Filter by status and integration
    $ ezkey admin auth-attempt list --status PENDING --integration-id 7

      # Second page, 10 per page, sorted by enrollment ID
      $ ezkey admin auth-attempt list --page 1 --size 10 --sort enrollmentId,asc --summary
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    try:
        validate_pagination_options(page, size, sort, 'auth-attempt')

        query_params = build_pagination_params(page, size, sort)
        if enrollment_id:
            query_params['enrollmentId'] = enrollment_id
        if status:
            query_params['status'] = status
        if integration_id:
            query_params['integrationId'] = integration_id
        if created_after:
            query_params['createdAfter'] = created_after
        if created_before:
            query_params['createdBefore'] = created_before

        url = f"{admin_url}/api/v1/auth-attempts"
        OutputUtils.verbose(f"GET {url}", verbose)
        OutputUtils.verbose(f"Params: {query_params}", verbose)

        response = http_client.get(url, params=query_params)

        if summary and response.success and response.data:
            display_page_summary(response.data, verbose=verbose)

        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)

    except ValueError as e:
        OutputUtils.error(str(e))
        ctx.exit(1)
    except Exception as e:
        OutputUtils.error(f"Failed to list auth attempts: {str(e)}")
        ctx.exit(1)


@auth_attempt_group.command('get')
@click.option('--id', required=True, type=int, help='Auth attempt ID')
@click.pass_context
def get_auth_attempt(ctx, id):
    """
    Get detailed information about a specific authentication attempt.

    Returns auth attempt details including status, proof token, challenge code,
    timestamps (created, expires, completed), and validation results.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/auth-attempts/{id}"
    OutputUtils.verbose(f"GET {url}", verbose)

    response = http_client.get(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@auth_attempt_group.command('create')
@click.option('--enrollment-id', required=True, type=int, help='Enrollment ID')
@click.option('--challenge-requested', is_flag=True, help='Request challenge for this attempt')
@click.option('--data', help='JSON data (or @filename for file input)')
@click.pass_context
def create_auth_attempt(ctx, enrollment_id, challenge_requested, data):
    """
    Create a new authentication attempt for MFA.

    This initiates an MFA flow where the device will be notified to approve
    or reject the authentication. Use --challenge-requested to require a
    6-digit challenge code for enhanced security.

    The device polls for pending attempts and responds with approval/rejection.
    Use 'ezkey admin auth-attempt wait' to wait for the device response.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    # Process data input
    if data:
        try:
            json_data = JsonUtils.process_input(data)
        except Exception as e:
            OutputUtils.error(f"Invalid JSON data: {str(e)}")
            return
    else:
        json_data = {}

    # Add required fields
    json_data['enrollmentId'] = enrollment_id
    if challenge_requested:
        json_data['challengeRequested'] = True

    url = f"{admin_url}/api/v1/auth-attempts"
    OutputUtils.verbose(f"POST {url}", verbose)
    if verbose:
        OutputUtils.verbose(f"Data: {JsonUtils.format_output(json_data)}", verbose)

    response = http_client.post(url, json_data=json_data)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@auth_attempt_group.command('wait')
@click.option('--id', required=True, type=int, help='Auth attempt ID')
@click.option('--timeout', default=30, help='Timeout in seconds (default: 30)', type=int)
@click.option('--polling', default=2, help='Polling interval in seconds (default: 2)', type=int)
@click.pass_context
def wait_for_auth_attempt(ctx, id, timeout, polling):
    """
    Wait for authentication attempt completion.

    Polls the auth attempt until it reaches a terminal state:
    - APPROVED: User approved the authentication
    - REJECTED: User rejected the authentication
    - EXPIRED: Auth attempt expired or was superseded

    Use this for synchronous authentication flows.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    if timeout < 1 or timeout > 300:
        OutputUtils.error("--timeout must be between 1 and 300 seconds")
        ctx.exit(1)

    if polling < 1 or polling > 60:
        OutputUtils.error("--polling must be between 1 and 60 seconds")
        ctx.exit(1)

    url = f"{admin_url}/api/v1/auth-attempts/{id}/wait"
    params = {'timeout': timeout, 'polling': polling}

    OutputUtils.info(
        f"Waiting for auth attempt {id} to complete "
        f"(timeout: {timeout}s, polling: {polling}s)..."
    )
    OutputUtils.verbose(f"GET {url}", verbose)
    OutputUtils.verbose(f"Params: {params}", verbose)

    response = http_client.get(url, params=params)

    if response.success:
        data = response.data or {}
        status_text = data.get('status')
        if status_text:
            OutputUtils.success(f"Auth attempt completed with status: {status_text}")
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@auth_attempt_group.command('cancel')
@click.option('--id', required=True, type=int, help='Auth attempt ID')
@click.pass_context
def cancel_auth_attempt(ctx, id):
    """
    Cancel an authentication attempt (marks it as expired).

    This does not delete the record; it only ends the attempt early.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/auth-attempts/{id}/cancel"
    OutputUtils.verbose(f"POST {url}", verbose)

    response = http_client.post(url, json_data={})

    if response.success:
        OutputUtils.success(f"✅ Auth attempt {id} cancelled successfully")
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


# Audit log commands
@admin_group.group('audit-log')
@click.pass_context
def audit_log_group(ctx):
    """Audit log query commands."""
    pass


@audit_log_group.command('list')
@click.option('--event-type', help='Filter by event type (e.g., ADMIN_LOGIN)')
@click.option('--event-status', help='Filter by event status (e.g., SUCCESS)')
@click.option('--api-name', help='Filter by API name (e.g., ADMIN_API)')
@click.option('--enrollment-id', type=int, help='Filter by enrollment ID')
@click.option('--admin-id', type=int, help='Filter by admin ID')
@click.option('--page', type=int, default=0, help='Page number (0-based, default: 0)')
@click.option('--size', type=int, default=20, help='Results per page (default: 20)')
@click.option('--sort', type=str, default='createdAt,desc',
              help='Sort by field (field,asc|desc, default: createdAt,desc)')
@click.option('--summary', is_flag=True, help='Show pagination metadata')
@click.pass_context
def list_audit_logs(ctx, event_type, event_status, api_name, enrollment_id, admin_id, page, size, sort, summary):
    """
    List audit logs with pagination, sorting, and filtering.

    Pagination: Results are returned in pages. Use --page to navigate.
    Page numbers start at 0.

    Sortable fields:
      auditLogId      - Audit log ID
      createdAt       - Creation date (default sort field)
      eventType       - Event type (e.g., ADMIN_LOGIN)
      eventStatus     - Event status (e.g., SUCCESS)
      apiName         - API name (e.g., ADMIN_API)

    Examples:
      # First page (default)
      $ ezkey admin audit-log list

      # With pagination info
      $ ezkey admin audit-log list --summary

      # Filter by event type, show summary
      $ ezkey admin audit-log list --event-type ADMIN_LOGIN --summary

      # Second page, 10 per page, sorted by event type
      $ ezkey admin audit-log list --page 1 --size 10 --sort eventType,asc --summary
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    try:
        validate_pagination_options(page, size, sort, 'audit-log')

        query_params = build_pagination_params(page, size, sort)
        if event_type:
            query_params['eventType'] = event_type
        if event_status:
            query_params['eventStatus'] = event_status
        if api_name:
            query_params['apiName'] = api_name
        if enrollment_id is not None:
            query_params['enrollmentId'] = enrollment_id
        if admin_id is not None:
            query_params['adminId'] = admin_id

        url = f"{admin_url}/api/v1/audit-logs"
        OutputUtils.verbose(f"GET {url}", verbose)
        if verbose:
            OutputUtils.verbose(f"Params: {query_params}", verbose)

        response = http_client.get(url, params=query_params)

        if summary and response.success and response.data:
            display_page_summary(response.data, verbose=verbose)

        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)

    except ValueError as e:
        OutputUtils.error(str(e))
        ctx.exit(1)
    except Exception as e:
        OutputUtils.error(f"Failed to list audit logs: {str(e)}")
        ctx.exit(1)


# Admin authentication commands
@admin_group.group('auth')
@click.pass_context
def auth_group(ctx):
    """Admin authentication commands."""
    pass


@auth_group.command('login')
@click.option('--username', required=True, help='Admin username')
@click.option('--challenge', is_flag=True, help='Request challenge code (two-step flow)')
@click.option('--no-save-token', is_flag=True, default=False, help='Do NOT save bearer token to config (default: save token)')
@click.pass_context
def admin_login(ctx, username, challenge, no_save_token):
    """
    Authenticate admin user using passwordless login.

    This command uses Ezkey's cryptographic authentication (no passwords).
    The admin must have a bound device enrollment to approve the login.

    Two modes:
    - Single-call (default): Blocks until device approves/rejects
    - Two-call (--challenge): Returns challenge code, requires separate wait

    By default, the bearer token is saved to the configuration file
    (~/.ezkey/ezkey.json) so subsequent commands don't require re-authentication.
    Use --no-save-token to skip saving the token.
    """
    config: ConfigManager = ctx.obj['config']
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    # save_token is True by default, False if --no-save-token is passed
    save_token = not no_save_token

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    # For login operations, use extended timeout (6 minutes) to allow device response
    # The server waits up to 5 minutes for device approval, so we need at least 6 minutes
    login_timeout_seconds = HttpClient.LOGIN_TIMEOUT_MS / 1000.0
    http_client = HttpClient(config, custom_timeout=login_timeout_seconds)

    # Prepare request data
    json_data = {
        'username': username,
        'challengeRequested': challenge
    }

    url = f"{admin_url}/api/v1/admin/auth/login"
    OutputUtils.verbose(f"POST {url}", verbose)
    OutputUtils.verbose(f"Timeout: {login_timeout_seconds}s (extended for login)", verbose)
    OutputUtils.info(f"Authenticating admin user: {username}")

    if challenge:
        OutputUtils.info("Two-step mode: Challenge code will be displayed")
    else:
        OutputUtils.info("Single-call mode: Waiting for device approval...")
        OutputUtils.info("⏳ This may take up to 5 minutes. Please approve on your device.")

    response = http_client.post(url, json_data=json_data)

    # Handle timeout errors specifically
    if not response.success and response.error and 'timeout' in response.error.lower():
        OutputUtils.error("⏱️ Request timeout - the device may not have responded in time")
        OutputUtils.info("Possible reasons:")
        OutputUtils.info("  - Device is not connected or enrolled")
        OutputUtils.info("  - Device did not approve/reject the authentication")
        OutputUtils.info("  - Network connectivity issues")
        OutputUtils.info("")
        OutputUtils.info("Try again or use --challenge mode for two-step authentication")
        return

    # Check if we have response data
    if response.data:
        data = response.data if isinstance(response.data, dict) else {}

        # Challenge mode - pending with challenge code (now returns HTTP 200 from server)
        if data.get('status') == 'pending' and data.get('challengeCode') and data.get('authAttemptId'):
            auth_attempt_id = data['authAttemptId']
            challenge_code = data['challengeCode']
            waiter_secret = data.get('waiterSecret')

            OutputUtils.info("")
            OutputUtils.warning("⏳ Authentication pending - Challenge verification required")
            OutputUtils.info("")
            OutputUtils.info("📱 On your device:")
            OutputUtils.info(f"   1. Enter challenge code: {challenge_code}")
            OutputUtils.info("   2. Approve the authentication request")
            OutputUtils.info("")
            OutputUtils.info("💻 Then run this command:")
            OutputUtils.info("")
            OutputUtils.info(f"   ezkey admin auth passwordless-wait \\")
            OutputUtils.info(f"     --auth-attempt-id {auth_attempt_id} \\")
            OutputUtils.info(f"     --challenge-code {challenge_code} \\")
            OutputUtils.info(f"     --waiter-secret {waiter_secret}")
            OutputUtils.info("")
            OutputUtils.info("   Or copy-paste this:")
            OutputUtils.info(
                f"   ezkey admin auth passwordless-wait --auth-attempt-id {auth_attempt_id} "
                f"--challenge-code {challenge_code} --waiter-secret {waiter_secret}"
            )
            OutputUtils.info("")

            if pretty_print:
                OutputUtils.output_json(data)
            return

        # Check if we got a token (single-call success)
        # The server returns success=true AND status="approved" AND token when successful
        if data.get('success') and data.get('token') and data.get('status') == 'approved':
            token = data['token']
            OutputUtils.success(f"✅ Authentication successful!")
            OutputUtils.info(f"Admin type: {data.get('adminType')}")
            OutputUtils.info(f"Token expires: {data.get('expiresAt')}")

            # Save token to config if requested
            if save_token:
                config.set_bearer_token(token)
                # Check if a local config file exists in current directory
                # If yes, save there too (it has higher precedence)
                # Otherwise, save to home directory
                local_config_path = Path.cwd() / "ezkey.json"
                if local_config_path.exists():
                    config.save(global_config=False)  # Save locally
                    OutputUtils.success("Bearer token saved to local config (./ezkey.json)")
                else:
                    config.save(global_config=True)   # Save to home
                    OutputUtils.success("Bearer token saved to global config (~/.ezkey/ezkey.json)")

            if pretty_print:
                OutputUtils.output_json(data)
            return

        # Failed authentication
        if not response.success:
            error_msg = data.get('message', 'Unknown error')
            OutputUtils.error(f"❌ Authentication failed: {error_msg}")

            # Provide helpful guidance for common errors
            if 'no device enrolled' in error_msg.lower() or 'no bound enrollment' in error_msg.lower():
                OutputUtils.info("")
                OutputUtils.info("💡 To fix this:")
                OutputUtils.info("  1. Ensure your device is enrolled and bound")
                OutputUtils.info("  2. Check enrollment status: ezkey admin enrollment list")
                OutputUtils.info("  3. If needed, reset enrollment: ezkey admin enrollment reset --id <id>")

            if pretty_print:
                OutputUtils.output_json(data)
            return

    # Handle HTTP errors (401, 403, etc.)
    if response.status == 401 or response.status == 403:
        OutputUtils.error("❌ Authentication failed")
        if response.error:
            OutputUtils.error(f"   {response.error}")
        OutputUtils.info("")
        OutputUtils.info("💡 Try logging in again: ezkey admin auth login --username admin")
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@auth_group.command('passwordless-wait')
@click.option('--auth-attempt-id', required=True, type=int, help='Auth attempt ID from login')
@click.option('--challenge-code', type=int, help='Challenge code from login (optional, shown in login output)')
@click.option('--waiter-secret', required=True, help='One-time waiter secret from login (shown in login output)')
@click.option('--no-save-token', is_flag=True, default=False, help='Do NOT save bearer token to config (default: save token)')
@click.pass_context
def admin_passwordless_wait(ctx, auth_attempt_id, challenge_code, waiter_secret, no_save_token):
    """
    Wait for device approval in two-step passwordless authentication.

    Use this after 'ezkey admin auth login --challenge' to complete authentication.
    The device must enter the matching challenge code before approval. The waiter
    secret proves this CLI session is the one that initiated the login; the Admin API
    rejects the request without it.

    By default, the bearer token is saved to the configuration file
    (~/.ezkey/ezkey.json) so subsequent commands don't require re-authentication.
    Use --no-save-token to skip saving the token.
    """
    # save_token is True by default, False if --no-save-token is passed
    save_token = not no_save_token

    config: ConfigManager = ctx.obj['config']
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    # Challenge code is required by the server for security
    if not challenge_code:
        OutputUtils.error("❌ Challenge code is required")
        OutputUtils.info("")
        OutputUtils.info("💡 The challenge code was shown in the login output.")
        OutputUtils.info("   If you don't have it, run the login command again:")
        OutputUtils.info("   ezkey admin auth login --username admin --challenge")
        return

    # For passwordless-wait, use extended timeout (6 minutes) to allow device response
    wait_timeout_seconds = HttpClient.LOGIN_TIMEOUT_MS / 1000.0
    http_client = HttpClient(config, custom_timeout=wait_timeout_seconds)

    json_data = {
        'authAttemptId': auth_attempt_id,
        'challengeCode': challenge_code,
        'waiterSecret': waiter_secret
    }

    url = f"{admin_url}/api/v1/admin/auth/passwordless-wait"
    OutputUtils.verbose(f"POST {url}", verbose)
    OutputUtils.verbose(f"Timeout: {wait_timeout_seconds}s (extended for wait)", verbose)
    OutputUtils.info(f"Waiting for device approval (auth attempt {auth_attempt_id})...")
    OutputUtils.info("⏳ This may take up to 5 minutes. Please approve on your device.")

    response = http_client.post(url, json_data=json_data)

    # Handle timeout errors specifically
    if not response.success and response.error and 'timeout' in response.error.lower():
        OutputUtils.error("⏱️ Request timeout - the device may not have responded in time")
        OutputUtils.info("Possible reasons:")
        OutputUtils.info("  - Challenge code was not entered correctly on device")
        OutputUtils.info("  - Device did not approve/reject the authentication")
        OutputUtils.info("  - Network connectivity issues")
        OutputUtils.info("")
        OutputUtils.info("Try again with the correct challenge code")
        return

    if response.success and response.data:
        data = response.data

        # Check if we got a token (successful authentication)
        # The server returns success=true AND status="approved" AND token when successful
        if data.get('success') and data.get('token') and data.get('status') == 'approved':
            token = data['token']
            OutputUtils.success(f"✅ Authentication successful!")
            OutputUtils.info(f"Admin type: {data.get('adminType')}")
            OutputUtils.info(f"Token expires: {data.get('expiresAt')}")

            # Save token to config if requested
            if save_token:
                config.set_bearer_token(token)
                # Check if a local config file exists in current directory
                # If yes, save there too (it has higher precedence)
                # Otherwise, save to home directory
                local_config_path = Path.cwd() / "ezkey.json"
                if local_config_path.exists():
                    config.save(global_config=False)  # Save locally
                    OutputUtils.success("Bearer token saved to local config (./ezkey.json)")
                else:
                    config.save(global_config=True)   # Save to home
                    OutputUtils.success("Bearer token saved to global config (~/.ezkey/ezkey.json)")

            if pretty_print:
                OutputUtils.output_json(data)
        else:
            error_msg = data.get('message', 'Unknown error')
            OutputUtils.error(f"❌ Authentication failed: {error_msg}")

            # Provide helpful guidance for common errors
            if 'challenge' in error_msg.lower() or 'invalid' in error_msg.lower():
                OutputUtils.info("")
                OutputUtils.info("💡 Make sure:")
                OutputUtils.info("  - The challenge code matches what was displayed")
                OutputUtils.info("  - The challenge code was entered on the device")
                OutputUtils.info("  - The device approved the authentication")

            if pretty_print:
                OutputUtils.output_json(data)
    else:
        # Handle HTTP errors (401, 403, etc.)
        if response.status == 401 or response.status == 403:
            OutputUtils.error("❌ Authentication failed")
            if response.error:
                OutputUtils.error(f"   {response.error}")
            OutputUtils.info("")
            OutputUtils.info("💡 Try logging in again: ezkey admin auth login --username admin")
        else:
            OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@auth_group.command('recover')
@click.option('--username', required=True, help='Admin username')
@click.option('--recovery-code', required=True, help='Recovery code (32-digit format: XXXX-XXXX-...)')
@click.option('--no-save-token', is_flag=True, default=False, help='Do NOT save bearer token to config (default: save token)')
@click.pass_context
def admin_recover(ctx, username, recovery_code, no_save_token):
    """
    Authenticate using recovery code (emergency access when device is lost).

    Recovery codes are 32-digit codes in format: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX
    Each code can only be used once. Token is valid for 30 minutes.

    After recovery, use 'ezkey admin enrollment reset' to unbind the lost device.

    By default, the bearer token is saved to the configuration file
    (~/.ezkey/ezkey.json) so subsequent commands don't require re-authentication.
    Use --no-save-token to skip saving the token.
    """
    # save_token is True by default, False if --no-save-token is passed
    save_token = not no_save_token

    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    json_data = {
        'username': username,
        'recoveryCode': recovery_code
    }

    url = f"{admin_url}/api/v1/admin/auth/recover"
    OutputUtils.verbose(f"POST {url}", verbose)
    OutputUtils.info(f"Attempting recovery for user: {username}")

    # Validate recovery code format before sending (client-side validation)
    # This provides immediate feedback without waiting for server response
    if recovery_code:
        parts = recovery_code.split('-')
        if (
            len(parts) != 8
            or any(len(part) != 4 for part in parts)
            or not all(part.isdigit() for part in parts)
        ):
            cleaned_code = recovery_code.replace('-', '')
            OutputUtils.error("Invalid recovery code format")
            OutputUtils.info("")
            OutputUtils.info("💡 Recovery code must be in format: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX")
            OutputUtils.info("   Example: 1234-5678-9012-3456-7890-1234-5678-9012")
            OutputUtils.info("")
            OutputUtils.info(f"   Your code: {recovery_code} (length: {len(cleaned_code)} digits)")
            return

    response = http_client.post(url, json_data=json_data)

    # Handle validation errors (now returns HTTP 400 with detailed message from backend)
    if not response.success and response.status == 400:
        error_msg = response.error or ""
        error_data = response.data if isinstance(response.data, dict) else {}

        # Check if this is a validation error (backend now returns 400 with VALIDATION_ERROR)
        if isinstance(error_data, dict):
            error_type = error_data.get('error', '')
            error_message = error_data.get('message', '')

            # Backend now returns validation errors as 400 with VALIDATION_ERROR type
            if 'VALIDATION_ERROR' in error_type or 'validation' in error_message.lower():
                OutputUtils.error("Invalid recovery code format")
                OutputUtils.info("")
                # Extract and display the validation message from backend
                if error_message:
                    OutputUtils.info(f"💡 {error_message}")
                else:
                    OutputUtils.info("💡 Recovery code must be 32 digits in format: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX")
                OutputUtils.info("")
                if verbose:
                    OutputUtils.verbose(f"Full error: {error_data}", verbose=True)
                return

    # Legacy workaround: Handle HTTP 500 errors (should not happen with fixed backend)
    # Keeping for backward compatibility with older backend versions
    if not response.success and response.status == 500:
        error_msg = response.error or ""
        error_data = response.data if isinstance(response.data, dict) else {}

        # Check if this is actually a validation error (legacy backend issue)
        if "unexpected error" in error_msg.lower() or "internal" in error_msg.lower():
            # Even though server returned 500, this might be a validation error
            OutputUtils.error("Invalid recovery code")
            OutputUtils.info("")
            OutputUtils.info("💡 The recovery code format is invalid.")
            OutputUtils.info("   Recovery code must be 32 digits in format: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX")
            OutputUtils.info("")
            if verbose:
                OutputUtils.verbose(f"Server response: {error_data.get('message', 'No details')}", verbose=True)
            return

    if response.success and response.data:
        data = response.data

        if data.get('success') and data.get('recoveryToken'):
            token = data['recoveryToken']
            OutputUtils.success(f"✅ Recovery successful!")
            OutputUtils.warning(f"⚠️  Recovery token valid for 30 minutes only")
            OutputUtils.warning(f"⚠️  Limited permissions: enrollment reset only")
            OutputUtils.warning(f"Codes remaining: {data.get('codesRemaining')}")
            OutputUtils.info(f"Token expires: {data.get('expiresAt')}")
            OutputUtils.info("")
            OutputUtils.info("Next steps:")
            OutputUtils.info("1. Use 'ezkey admin enrollment reset --id <enrollment-id>' to unbind lost device")
            OutputUtils.info("2. Bind new device with the new credentials")
            OutputUtils.info("3. After binding, use 'ezkey admin auth login' for full access")

            # Save recovery token to config if requested (separate from bearer token)
            if save_token:
                config.set_recovery_token(token)
                config.save(global_config=True)
                OutputUtils.success("Recovery token saved to config")
                OutputUtils.warning("⚠️  Note: Recovery token has limited permissions")

            if pretty_print:
                OutputUtils.output_json(data)
        else:
            OutputUtils.error(f"Recovery failed: {data.get('message', 'Unknown error')}")
            if pretty_print:
                OutputUtils.output_json(data)
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@auth_group.command('logout')
@click.pass_context
def admin_logout(ctx):
    """
    Logout and revoke current bearer token.

    This invalidates the current session token in the server.
    The token is also removed from local config.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    # Check if we have a token (bearer or recovery)
    has_bearer = config.get('bearerToken') is not None
    has_recovery = config.has_recovery_token()

    if not has_bearer and not has_recovery:
        OutputUtils.warning("No authentication token found in config")
        return

    url = f"{admin_url}/api/v1/admin/auth/logout"
    OutputUtils.verbose(f"POST {url}", verbose)

    if has_recovery:
        OutputUtils.info("Logging out (recovery token)...")
    else:
        OutputUtils.info("Logging out...")

    response = http_client.post(url)

    if response.success:
        OutputUtils.success("✅ Logout successful")

        # Clear tokens from config
        if has_bearer:
            config.clear_bearer_token()
        if has_recovery:
            config.clear_recovery_token()
        config.save(global_config=True)
        OutputUtils.success("Authentication token removed from config")
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)

        # Still clear tokens from config even if logout failed
        if has_bearer:
            config.clear_bearer_token()
        if has_recovery:
            config.clear_recovery_token()
        config.save(global_config=True)
        OutputUtils.info("Authentication token removed from config")


# Encryption Keys management commands
@admin_group.group('encryption-key')
@click.pass_context
def encryption_key_group(ctx):
    """Encryption key lifecycle management and rotation operations."""
    pass


@encryption_key_group.command('list')
@click.pass_context
def list_encryption_keys(ctx):
    """
    List all encryption keys in the system.

    Returns all encryption keys with their status, algorithm, timestamps,
    and usage statistics (records encrypted, records reencrypted).
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/encryption-keys"
    OutputUtils.verbose(f"GET {url}", verbose)

    response = http_client.get(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@encryption_key_group.command('get')
@click.option('--id', 'key_id', required=True, type=int, help='Encryption key ID')
@click.pass_context
def get_encryption_key(ctx, key_id):
    """
    Get detailed information about a specific encryption key.

    Returns key details including status, algorithm, timestamps,
    usage statistics, and metadata.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/encryption-keys/{key_id}"
    OutputUtils.verbose(f"GET {url}", verbose)

    response = http_client.get(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@encryption_key_group.command('primary')
@click.pass_context
def get_primary_encryption_key(ctx):
    """
    Get the current primary encryption key.

    Returns the primary encryption key used for new encryption operations.
    This is the key that will be used when encrypting new data.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/encryption-keys/primary"
    OutputUtils.verbose(f"GET {url}", verbose)

    response = http_client.get(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@encryption_key_group.command('rotate')
@click.confirmation_option(prompt='Are you sure you want to rotate the encryption key? This will create a new primary key.')
@click.pass_context
def rotate_encryption_key(ctx):
    """
    Manually trigger encryption key rotation.

    Immediately rotates the encryption key, creating a new primary key.
    The old primary key becomes a regular key and can be used for decryption
    of existing data. New data will be encrypted with the new primary key.

    WARNING: This operation is critical and should be performed during
    maintenance windows. Re-encryption of existing data should follow.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/encryption-keys/rotate"
    OutputUtils.verbose(f"POST {url}", verbose)
    OutputUtils.info("Rotating encryption key...")

    response = http_client.post(url)

    if response.success and response.data:
        data = response.data
        new_key_id = data.get('newPrimaryKeyId')
        if new_key_id:
            OutputUtils.success(f"✅ Key rotation completed successfully")
            OutputUtils.info(f"New primary key ID: {new_key_id}")
            OutputUtils.info("💡 Consider running re-encryption to migrate existing data to the new key")
        else:
            OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


# Re-encryption operations
@encryption_key_group.group('reencrypt')
@click.pass_context
def reencrypt_group(ctx):
    """Re-encryption batch management and operations."""
    pass


@reencrypt_group.command('trigger')
@click.option('--key-id', type=int, help='Trigger re-encryption for specific key (optional, triggers full if omitted)')
@click.pass_context
def trigger_reencryption(ctx, key_id):
    """
    Trigger re-encryption process.

    If --key-id is provided, creates and processes re-encryption batches
    for that specific old key (must not be PRIMARY).

    If --key-id is omitted, triggers full re-encryption for all old keys,
    creating batches and processing them immediately.

    This operation migrates data encrypted with old keys to the current
    primary key for improved security and key lifecycle management.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    if key_id:
        # Trigger re-encryption for specific key
        url = f"{admin_url}/api/v1/encryption-keys/{key_id}/reencrypt"
        OutputUtils.verbose(f"POST {url}", verbose)
        OutputUtils.info(f"Triggering re-encryption for key {key_id}...")

        response = http_client.post(url)

        if response.success and response.data:
            data = response.data
            OutputUtils.success(f"✅ Re-encryption triggered successfully")
            OutputUtils.info(f"Batches created: {data.get('batchesCreated', 0)}")
            OutputUtils.info(f"Batches processed: {data.get('batchesProcessed', 0)}")
            OutputUtils.info(f"Batches failed: {data.get('batchesFailed', 0)}")
            if data.get('message'):
                OutputUtils.info(f"Message: {data.get('message')}")
        else:
            OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)
    else:
        # Trigger full re-encryption
        url = f"{admin_url}/api/v1/encryption-keys/reencrypt/trigger"
        OutputUtils.verbose(f"POST {url}", verbose)
        OutputUtils.info("Triggering full re-encryption for all old keys...")

        response = http_client.post(url)

        if response.success and response.data:
            data = response.data
            OutputUtils.success(f"✅ Full re-encryption triggered successfully")
            OutputUtils.info(f"Batches created: {data.get('batchesCreated', 0)}")
            OutputUtils.info(f"Batches processed: {data.get('batchesProcessed', 0)}")
            OutputUtils.info(f"Batches failed: {data.get('batchesFailed', 0)}")
            if data.get('message'):
                OutputUtils.info(f"Message: {data.get('message')}")
        else:
            OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@reencrypt_group.command('create-batches')
@click.pass_context
def create_reencryption_batches(ctx):
    """
    Create re-encryption batches without processing them.

    Creates re-encryption batches for all old keys without processing them.
    Batches will be processed by the scheduled job automatically.

    Use this when you want to prepare batches for background processing
    rather than immediate execution.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/encryption-keys/reencrypt/create-batches"
    OutputUtils.verbose(f"POST {url}", verbose)
    OutputUtils.info("Creating re-encryption batches...")

    response = http_client.post(url)

    if response.success and response.data:
        data = response.data
        OutputUtils.success(f"✅ Batches created successfully")
        OutputUtils.info(f"Batches created: {data.get('batchesCreated', 0)}")
        OutputUtils.info("💡 Batches will be processed by the scheduled job")
        if data.get('message'):
            OutputUtils.info(f"Message: {data.get('message')}")
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@reencrypt_group.command('batches')
@click.option('--status', type=str,
              help='Filter by batch status (PENDING, IN_PROGRESS, COMPLETED, FAILED, PAUSED)')
@click.option('--target-table', type=str, help='Exact match on target table name')
@click.option('--target-column', type=str, help='Exact match on target column name')
@click.option('--old-key-id', type=int, help='Filter by old encryption key id')
@click.option('--new-key-id', type=int, help='Filter by new encryption key id')
@click.option('--created-after', type=str,
              help='Inclusive lower bound on createdAt (ISO-8601, e.g. 2025-01-31T00:00:00Z)')
@click.option('--created-before', type=str,
              help='Inclusive upper bound on createdAt (ISO-8601, e.g. 2025-01-31T23:59:59Z)')
@click.option('--page', type=int, default=0, help='Page number (0-based, default: 0)')
@click.option('--size', type=int, default=20, help='Results per page (default: 20)')
@click.option('--sort', type=str, default='createdAt,desc',
              help='Sort by field (field,asc|desc, default: createdAt,desc)')
@click.option('--summary', is_flag=True, help='Show pagination metadata')
@click.pass_context
def list_reencryption_batches(
    ctx,
    status,
    target_table,
    target_column,
    old_key_id,
    new_key_id,
    created_after,
    created_before,
    page,
    size,
    sort,
    summary,
):
    """
    List re-encryption batches with server-side pagination and optional filters.

    Response body matches other admin lists: content (array) and page (metadata).

    Sortable fields include batchId, status, targetTable, targetColumn, createdAt,
    startedAt, completedAt, progressPct, recordsTotal, recordsDone, oldKey.keyId, newKey.keyId.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    try:
        validate_pagination_options(page, size, sort, 'reencryption-batch')

        query_params = build_pagination_params(page, size, sort)
        if status:
            query_params['status'] = status
        if target_table:
            query_params['targetTable'] = target_table
        if target_column:
            query_params['targetColumn'] = target_column
        if old_key_id is not None:
            query_params['oldKeyId'] = old_key_id
        if new_key_id is not None:
            query_params['newKeyId'] = new_key_id
        if created_after:
            query_params['createdAfter'] = created_after
        if created_before:
            query_params['createdBefore'] = created_before

        url = f"{admin_url}/api/v1/encryption-keys/reencryption-batches"
        OutputUtils.verbose(f"GET {url}", verbose)
        OutputUtils.verbose(f"Params: {query_params}", verbose)

        response = http_client.get(url, params=query_params)

        if summary and response.success and response.data:
            display_page_summary(response.data, verbose=verbose)

        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)

    except (ValueError, click.BadParameter) as e:
        OutputUtils.error(str(e))
        ctx.exit(1)
    except Exception as e:
        OutputUtils.error(f"Failed to list re-encryption batches: {str(e)}")
        ctx.exit(1)


@reencrypt_group.command('resume')
@click.option('--batch-id', required=True, type=int, help='Re-encryption batch ID to resume')
@click.pass_context
def resume_reencryption_batch(ctx, batch_id):
    """
    Resume processing of a failed or paused re-encryption batch.

    Resumes a batch that was previously paused or failed. The batch will
    continue processing from where it left off.

    Use this to recover from transient failures or to resume paused batches.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/encryption-keys/reencryption-batches/{batch_id}/resume"
    OutputUtils.verbose(f"POST {url}", verbose)
    OutputUtils.info(f"Resuming re-encryption batch {batch_id}...")

    response = http_client.post(url)

    if response.success and response.data:
        data = response.data
        OutputUtils.success(f"✅ Batch resumed successfully")
        if data.get('message'):
            OutputUtils.info(f"Message: {data.get('message')}")
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


# API Keys management commands
@admin_group.group('api-key')
@click.pass_context
def api_key_group(ctx):
    """API key management commands for machine-to-machine authentication."""
    pass


@api_key_group.command('create')
@click.option('--integration-id', required=True, type=int, help='Integration ID for the API key')
@click.option('--description', help='Description of the API key (e.g., "Production Server")')
@click.option('--expires-at', help='Expiration date in ISO 8601 format (e.g., "2025-12-31T23:59:59Z")')
@click.option('--ip-whitelist', multiple=True, help='IP addresses or CIDR ranges (can be specified multiple times)')
@click.option('--save-key', is_flag=True, help='Save API key credentials to config')
@click.pass_context
def create_api_key(ctx, integration_id, description, expires_at, ip_whitelist, save_key):
    """
    Create a new API key for machine-to-machine authentication.

    The secret key is shown ONLY ONCE and cannot be retrieved later.
    Save it immediately in a secure location.

    Example:
      ezkey admin api-key create --integration-id 123 --description "Production Server" \\
        --expires-at "2025-12-31T23:59:59Z" --ip-whitelist "192.168.1.0/24"
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    # Build request data
    json_data = {
        'integrationId': integration_id
    }

    if description:
        json_data['description'] = description

    if expires_at:
        json_data['expiresAt'] = expires_at

    if ip_whitelist:
        json_data['ipWhitelist'] = list(ip_whitelist)

    url = f"{admin_url}/api/v1/api-keys"
    OutputUtils.verbose(f"POST {url}", verbose)
    OutputUtils.info(f"Creating API key for integration {integration_id}...")

    response = http_client.post(url, json_data=json_data)

    if response.success and response.data:
        data = response.data

        OutputUtils.success("✅ API key created successfully!")
        OutputUtils.warning("⚠️  IMPORTANT: Save the secret key now. It will not be shown again.")
        OutputUtils.info("")
        OutputUtils.info(f"API Key ID: {data.get('apiKeyId')}")
        OutputUtils.info(f"Integration Key: {data.get('integrationKey')}")
        OutputUtils.info(f"Secret Key: {data.get('secretKey')}")
        OutputUtils.info(f"Description: {data.get('description')}")
        OutputUtils.info(f"Created: {data.get('createdAt')}")
        if data.get('expiresAt'):
            OutputUtils.info(f"Expires: {data.get('expiresAt')}")
        if data.get('ipWhitelist'):
            OutputUtils.info(f"IP Whitelist: {', '.join(data['ipWhitelist'])}")

        # Save API key to config if requested
        if save_key:
            config.set_api_key(data['integrationKey'], data['secretKey'])
            config.save(global_config=True)
            OutputUtils.success("API key saved to config")

        if pretty_print:
            OutputUtils.info("")
            OutputUtils.output_json(data)
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@api_key_group.command('list')
@click.option('--integration-id', required=True, type=int, help='Integration ID to list keys for')
@click.pass_context
def list_api_keys(ctx, integration_id):
    """
    List all API keys for a specific integration.

    Secret keys are never included in the response for security.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/api-keys/integration/{integration_id}"
    OutputUtils.verbose(f"GET {url}", verbose)

    response = http_client.get(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@api_key_group.command('list-all')
@click.pass_context
def list_all_api_keys(ctx):
    """
    List all API keys visible to the current admin.

    Secret keys are never included in the response for security.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/api-keys"
    OutputUtils.verbose(f"GET {url}", verbose)

    response = http_client.get(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@api_key_group.command('get')
@click.option('--id', required=True, type=int, help='API key ID')
@click.pass_context
def get_api_key(ctx, id):
    """
    Get details of a specific API key.

    Secret key is never included in the response for security.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/api-keys/{id}"
    OutputUtils.verbose(f"GET {url}", verbose)

    response = http_client.get(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@api_key_group.command('revoke')
@click.option('--id', required=True, type=int, help='API key ID to revoke')
@click.confirmation_option(prompt='Are you sure you want to revoke this API key?')
@click.pass_context
def revoke_api_key(ctx, id):
    """
    Revoke an API key (immediately unusable).

    Use this for:
    - Compromised key security incidents
    - Key rotation cleanup after deploying new key
    - Decommissioning an application

    The key is preserved for audit but cannot be used.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/api-keys/{id}"
    OutputUtils.verbose(f"DELETE {url}", verbose)
    OutputUtils.info(f"Revoking API key {id}...")

    response = http_client.delete(url)

    if response.success:
        OutputUtils.success(f"✅ API key {id} revoked successfully")
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


# Admin provisioning commands
@admin_group.group('provisioning')
@click.pass_context
def provisioning_group(ctx):
    """Admin provisioning commands for managing admin users."""
    pass


# Tenant management commands
@admin_group.group('tenant')
@click.pass_context
def tenant_group(ctx):
    """Tenant management commands (GlobalAdmin only)."""
    pass


@provisioning_group.command('list')
@click.option('--page', type=int, default=0, help='Page number (0-based, default: 0)')
@click.option('--size', type=int, default=20, help='Results per page (default: 20)')
@click.option('--sort', type=str, default='createdAt,desc',
              help='Sort by field (field,asc|desc, default: createdAt,desc)')
@click.option('--summary', is_flag=True, help='Show pagination metadata')
@click.pass_context
def list_admins(ctx, page, size, sort, summary):
    """
    List all provisioned admin users with pagination support.

    Provisioning manages admin user accounts for the Ezkey system.
    Each admin has MFA credentials and access control settings.

    Pagination: Results are returned in pages. Use --page to navigate.
    Page numbers start at 0.

    Sortable fields:
      id              - Admin ID
      createdAt       - Creation date (default sort field)

    Examples:
      # First page (default)
      $ ezkey admin provisioning list

      # With pagination info
      $ ezkey admin provisioning list --summary

      # Second page, 10 per page
      $ ezkey admin provisioning list --page 1 --size 10 --summary

      # Sort by ID ascending
      $ ezkey admin provisioning list --sort id,asc --summary
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    try:
        validate_pagination_options(page, size, sort, 'admin')

        query_params = build_pagination_params(page, size, sort)

        url = f"{admin_url}/api/v1/admins"
        OutputUtils.verbose(f"GET {url}", verbose)
        OutputUtils.verbose(f"Params: {query_params}", verbose)

        response = http_client.get(url, params=query_params)

        if summary and response.success and response.data:
            display_page_summary(response.data, verbose=verbose)

        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)

    except ValueError as e:
        OutputUtils.error(str(e))
        ctx.exit(1)
    except Exception as e:
        OutputUtils.error(f"Failed to list admins: {str(e)}")
        ctx.exit(1)


@provisioning_group.command('create-global')
@click.option('--username', required=True, help='Admin username (3-50 chars)')
@click.option('--email', required=True, help='Email address (required for global admins)')
@click.option('--first-name', required=True, help='First name (required for global admins)')
@click.option('--last-name', required=True, help='Last name (required for global admins)')
@click.pass_context
def create_global_admin(ctx, username, email, first_name, last_name):
    """
    Create a peer global administrator (GlobalAdmin only).
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    json_data = {
        'username': username,
        'email': email,
        'firstName': first_name,
        'lastName': last_name
    }

    url = f"{admin_url}/api/v1/admins/global"
    OutputUtils.verbose(f"POST {url}", verbose)

    response = http_client.post(url, json_data=json_data)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@provisioning_group.command('create-tenant')
@click.option('--username', required=True, help='Admin username (3-50 chars)')
@click.option('--tenant-id', required=True, type=int, help='Tenant ID')
@click.option('--email', help='Email address (optional for tenant admins)')
@click.option('--first-name', help='First name (optional for tenant admins)')
@click.option('--last-name', help='Last name (optional for tenant admins)')
@click.pass_context
def create_tenant_admin(ctx, username, tenant_id, email, first_name, last_name):
    """
    Create a peer tenant administrator.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    json_data = {
        'username': username,
        'tenantId': tenant_id
    }

    if email:
        json_data['email'] = email
    if first_name:
        json_data['firstName'] = first_name
    if last_name:
        json_data['lastName'] = last_name

    url = f"{admin_url}/api/v1/admins/tenant"
    OutputUtils.verbose(f"POST {url}", verbose)

    response = http_client.post(url, json_data=json_data)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@provisioning_group.command('onboarding')
@click.option('--id', required=True, type=int, help='Admin ID')
@click.pass_context
def get_admin_onboarding(ctx, id):
    """
    Retrieve onboarding credentials for an administrator.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/admins/{id}/onboarding"
    OutputUtils.verbose(f"GET {url}", verbose)

    response = http_client.get(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@provisioning_group.command('qrcode')
@click.option('--id', required=True, type=int, help='Admin ID')
@click.option('--output', '-o', help='Output file path (default: admin-{id}-onboarding-qrcode.png)')
@click.pass_context
def get_admin_onboarding_qrcode(ctx, id, output):
    """
    Generate onboarding QR code for an administrator.

    Returns a PNG QR code image containing enrollment credentials
    (enrollmentId|enrollmentProofToken) for passwordless enrollment binding.
    """
    config: ConfigManager = ctx.obj['config']
    verbose = ctx.obj.get('verbose', False)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    http_client = HttpClient(config)
    original_accept = http_client.session.headers.get('Accept')
    http_client.session.headers['Accept'] = 'image/png,*/*'

    url = f"{admin_url}/api/v1/admins/{id}/onboarding/qrcode"
    OutputUtils.verbose(f"GET {url}", verbose)

    try:
        response = http_client.session.get(url, timeout=http_client.timeout)

        if response.ok:
            if output:
                output_file = output
            else:
                output_file = f"admin-{id}-onboarding-qrcode.png"

            with open(output_file, 'wb') as f:
                f.write(response.content)

            OutputUtils.success(f"✅ QR code saved to {output_file}")
            OutputUtils.info(f"File size: {len(response.content)} bytes")
        else:
            try:
                error_data = response.json()
                error_msg = error_data.get('message', error_data.get('error', 'Unknown error'))
                OutputUtils.error(f"Failed to generate QR code: {error_msg}")
            except ValueError:
                OutputUtils.error(f"Failed to generate QR code: HTTP {response.status_code}")

            if response.status_code == 400:
                OutputUtils.info("💡 Enrollment may be missing proof token")
            elif response.status_code == 404:
                OutputUtils.info("💡 Administrator not found")
    except Exception as e:
        OutputUtils.error(f"Request failed: {str(e)}")
    finally:
        if original_accept:
            http_client.session.headers['Accept'] = original_accept
        else:
            http_client.session.headers.pop('Accept', None)


@provisioning_group.command('deactivate')
@click.option('--id', required=True, type=int, help='Admin ID to deactivate')
@click.confirmation_option(prompt='Are you sure you want to deactivate this admin?')
@click.pass_context
def deactivate_admin(ctx, id):
    """
    Deactivate an administrator (GlobalAdmin only).
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/admins/{id}/deactivate"
    OutputUtils.verbose(f"POST {url}", verbose)

    response = http_client.post(url, json_data={})

    if response.success:
        OutputUtils.success(f"✅ Admin {id} deactivated successfully")
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@tenant_group.command('create')
@click.option('--name', required=True, help='Tenant name (3-100 chars)')
@click.option('--description', help='Tenant description (max 500 chars)')
@click.pass_context
def create_tenant(ctx, name, description):
    """
    Create a tenant (GlobalAdmin only).
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    json_data = {
        'tenantName': name,
        'tenantDescription': description
    }

    url = f"{admin_url}/api/v1/tenants"
    OutputUtils.verbose(f"POST {url}", verbose)

    response = http_client.post(url, json_data=json_data)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@tenant_group.command('list')
@click.pass_context
def list_tenants(ctx):
    """
    List all tenants (GlobalAdmin only).
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/tenants"
    OutputUtils.verbose(f"GET {url}", verbose)

    response = http_client.get(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@tenant_group.command('get')
@click.option('--id', required=True, type=int, help='Tenant ID')
@click.pass_context
def get_tenant(ctx, id):
    """
    Get tenant details by ID (GlobalAdmin only).
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/tenants/{id}"
    OutputUtils.verbose(f"GET {url}", verbose)

    response = http_client.get(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@tenant_group.command('deactivate')
@click.option('--id', required=True, type=int, help='Tenant ID to deactivate')
@click.confirmation_option(prompt='Are you sure you want to deactivate this tenant?')
@click.pass_context
def deactivate_tenant(ctx, id):
    """
    Deactivate a tenant (GlobalAdmin only).
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    url = f"{admin_url}/api/v1/tenants/{id}/deactivate"
    OutputUtils.verbose(f"POST {url}", verbose)

    response = http_client.post(url, json_data={})

    if response.success:
        OutputUtils.success(f"✅ Tenant {id} deactivated successfully")
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


# Admin enrollment reset command
@enrollment_group.command('reset')
@click.option('--id', required=True, type=int, help='Enrollment ID to reset')
@click.pass_context
def reset_enrollment(ctx, id):
    """
    Reset enrollment after recovery (unbind lost device).

    This requires a recovery token (obtained via 'ezkey admin auth recover').
    The old device is unbound and new enrollment credentials are generated.
    Use the new credentials to bind a new device.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)

    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return

    # Check if we have a recovery token
    if not config.has_recovery_token():
        OutputUtils.error("❌ Recovery token required for enrollment reset")
        OutputUtils.info("")
        OutputUtils.info("💡 To fix this:")
        OutputUtils.info("  1. Use recovery code: ezkey admin auth recover --username admin --recovery-code <code>")
        OutputUtils.info("  2. Then retry: ezkey admin enrollment reset --id <id>")
        return

    json_data = {
        'enrollmentId': id
    }

    url = f"{admin_url}/api/v1/admin/enrollments/reset"
    OutputUtils.verbose(f"POST {url}", verbose)
    OutputUtils.info(f"Resetting enrollment {id}...")

    response = http_client.post(url, json_data=json_data)

    if response.success and response.data:
        data = response.data

        if data.get('success'):
            OutputUtils.success(f"✅ Enrollment reset successful!")
            OutputUtils.info(f"Enrollment ID: {data.get('enrollmentId')}")
            OutputUtils.info(f"New proof token: {data.get('enrollmentProofToken')}")
            OutputUtils.info(f"New challenge: {data.get('enrollmentChallenge')}")
            OutputUtils.info(f"Integration ID: {data.get('integrationId')}")
            OutputUtils.info("")
            OutputUtils.info("Use these credentials to bind a new device")

            if pretty_print:
                OutputUtils.output_json(data)
        else:
            OutputUtils.error(f"Reset failed: {data.get('message', 'Unknown error')}")
            if pretty_print:
                OutputUtils.output_json(data)
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


# Make the group available for import
admin = admin_group
