"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Admin Command
Description: Admin API commands for integrations, enrollments, and auth attempts
"""

from typing import Any, Dict

import click

from ..config import ConfigManager
from ..utils import HttpClient, JsonUtils, OutputUtils


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
@click.pass_context
def list_integrations(ctx):
    """
    List all integrations in the system.
    
    Integrations represent applications or systems protected by Ezkey MFA.
    Each integration has its own cryptographic keys and enrollments.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return
    
    url = f"{admin_url}/api/v1/integrations"
    OutputUtils.verbose(f"GET {url}", verbose)
    
    response = http_client.get(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@integration_group.command('get')
@click.option('--id', required=True, type=int, help='Integration ID')
@click.pass_context
def get_integration(ctx, id):
    """
    Get detailed information about a specific integration.
    
    Returns integration details including name, description, logo,
    public key, and active status for all configured languages.
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
@click.option('--logo', help='Logo URL or path')
@click.option('--data', help='JSON data (or @filename for file input)')
@click.pass_context
def create_integration(ctx, logo, data):
    """
    Create a new integration for MFA protection.
    
    An integration represents an application or system that will be protected
    by Ezkey MFA. Each integration has its own cryptographic keys and can have
    multiple enrollments (users/devices).
    
    Example JSON:
      {
        "logo": "https://example.com/logo.png",
        "i18n": [
          {
            "language": "en",
            "name": "My Application",
            "description": "My application description"
          }
        ]
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
    
    # Add logo if provided
    if logo:
        json_data['logo'] = logo
    
    if not json_data:
        OutputUtils.error("No data provided. Use --data option or --logo")
        return
    
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
@click.pass_context
def list_enrollments(ctx, integration_id):
    """
    List all enrollments in the system.
    
    Enrollments represent the association between a user/device and an integration.
    Each enrollment has cryptographic keys for secure authentication.
    
    Use --integration-id to filter by specific integration.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return
    
    url = f"{admin_url}/api/v1/enrollments"
    params = {}
    if integration_id:
        params['integrationId'] = integration_id
    
    OutputUtils.verbose(f"GET {url}", verbose)
    if params:
        OutputUtils.verbose(f"Params: {params}", verbose)
    
    response = http_client.get(url, params=params)
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
@click.option('--data', help='JSON data (or @filename for file input)')
@click.pass_context
def create_enrollment(ctx, integration_id, data):
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
    
    url = f"{admin_url}/api/v1/enrollments"
    OutputUtils.verbose(f"POST {url}", verbose)
    if verbose:
        OutputUtils.verbose(f"Data: {JsonUtils.format_output(json_data)}", verbose)
    
    response = http_client.post(url, json_data=json_data)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


# Auth attempt commands
@admin_group.group('auth-attempt')
@click.pass_context
def auth_attempt_group(ctx):
    """Authentication attempt management commands."""
    pass


@auth_attempt_group.command('list')
@click.option('--enrollment-id', type=int, help='Filter by enrollment ID')
@click.pass_context
def list_auth_attempts(ctx, enrollment_id):
    """
    List all authentication attempts in the system.
    
    Authentication attempts represent MFA requests that devices must approve
    or reject. Each attempt has a unique proof token and tracks its status
    (PENDING, READ, ACCEPTED, REJECTED, INVALID, EXPIRED).
    
    Use --enrollment-id to filter by specific enrollment.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return
    
    url = f"{admin_url}/api/v1/auth-attempts"
    params = {}
    if enrollment_id:
        params['enrollmentId'] = enrollment_id
    
    OutputUtils.verbose(f"GET {url}", verbose)
    if params:
        OutputUtils.verbose(f"Params: {params}", verbose)
    
    response = http_client.get(url, params=params)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


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
@click.option('--page', default=0, type=int, show_default=True, help='Page number (zero-based)')
@click.option('--size', default=20, type=int, show_default=True, help='Page size (max 100)')
@click.pass_context
def list_audit_logs(ctx, event_type, event_status, api_name, enrollment_id, admin_id, page, size):
    """
    Query audit logs with optional filters.
    
    Results are returned with pagination (page/size) mirroring the Admin API contract.
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return
    
    params = {
        'page': page,
        'size': size,
    }
    if event_type:
        params['eventType'] = event_type
    if event_status:
        params['eventStatus'] = event_status
    if api_name:
        params['apiName'] = api_name
    if enrollment_id is not None:
        params['enrollmentId'] = enrollment_id
    if admin_id is not None:
        params['adminId'] = admin_id
    
    url = f"{admin_url}/api/v1/audit-logs"
    OutputUtils.verbose(f"GET {url}", verbose)
    if verbose:
        OutputUtils.verbose(f"Params: {params}", verbose)
    
    response = http_client.get(url, params=params)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


# Admin authentication commands
@admin_group.group('auth')
@click.pass_context
def auth_group(ctx):
    """Admin authentication commands."""
    pass


@auth_group.command('login')
@click.option('--username', required=True, help='Admin username')
@click.option('--challenge', is_flag=True, help='Request challenge code (two-step flow)')
@click.option('--save-token', is_flag=True, default=True, help='Save bearer token to config')
@click.pass_context
def admin_login(ctx, username, challenge, save_token):
    """
    Authenticate admin user using passwordless login.
    
    This command uses Ezkey's cryptographic authentication (no passwords).
    The admin must have a bound device enrollment to approve the login.
    
    Two modes:
    - Single-call (default): Blocks until device approves/rejects
    - Two-call (--challenge): Returns challenge code, requires separate wait
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return
    
    # Prepare request data
    json_data = {
        'username': username,
        'challengeRequested': challenge
    }
    
    url = f"{admin_url}/api/v1/admin/auth/login"
    OutputUtils.verbose(f"POST {url}", verbose)
    OutputUtils.info(f"Authenticating admin user: {username}")
    
    if challenge:
        OutputUtils.info("Two-step mode: Challenge code will be displayed")
    else:
        OutputUtils.info("Single-call mode: Waiting for device approval...")
    
    response = http_client.post(url, json_data=json_data)
    
    if response.success and response.data:
        data = response.data
        
        # Check if we got a token (single-call success)
        if data.get('success') and data.get('token'):
            token = data['token']
            OutputUtils.success(f"✅ Authentication successful!")
            OutputUtils.info(f"Admin type: {data.get('adminType')}")
            OutputUtils.info(f"Token expires: {data.get('expiresAt')}")
            
            # Save token to config if requested
            if save_token:
                config.set_bearer_token(token)
                config.save(global_config=True)
                OutputUtils.success("Bearer token saved to config")
            
            if pretty_print:
                OutputUtils.output_json(data)
        
        # Challenge mode - need to wait
        elif data.get('status') == 'pending' and data.get('challengeCode'):
            OutputUtils.warning(f"⏳ Authentication pending")
            OutputUtils.info(f"Challenge code: {data['challengeCode']}")
            OutputUtils.info(f"Auth attempt ID: {data['authAttemptId']}")
            OutputUtils.info(f"Enter this code on your device, then run:")
            OutputUtils.info(f"  ezkey admin auth passwordless-wait --auth-attempt-id {data['authAttemptId']} --challenge-code {data['challengeCode']}")
            
            if pretty_print:
                OutputUtils.output_json(data)
        
        # Failed authentication
        else:
            OutputUtils.error(f"Authentication failed: {data.get('message', 'Unknown error')}")
            if pretty_print:
                OutputUtils.output_json(data)
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@auth_group.command('passwordless-wait')
@click.option('--auth-attempt-id', required=True, type=int, help='Auth attempt ID from login')
@click.option('--challenge-code', required=True, type=int, help='Challenge code from login')
@click.option('--save-token', is_flag=True, default=True, help='Save bearer token to config')
@click.pass_context
def admin_passwordless_wait(ctx, auth_attempt_id, challenge_code, save_token):
    """
    Wait for device approval in two-step passwordless authentication.
    
    Use this after 'ezkey admin auth login --challenge' to complete authentication.
    The device must enter the matching challenge code before approval.
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
        'authAttemptId': auth_attempt_id,
        'challengeCode': challenge_code
    }
    
    url = f"{admin_url}/api/v1/admin/auth/passwordless-wait"
    OutputUtils.verbose(f"POST {url}", verbose)
    OutputUtils.info(f"Waiting for device approval (auth attempt {auth_attempt_id})...")
    
    response = http_client.post(url, json_data=json_data)
    
    if response.success and response.data:
        data = response.data
        
        if data.get('success') and data.get('token'):
            token = data['token']
            OutputUtils.success(f"✅ Authentication successful!")
            OutputUtils.info(f"Admin type: {data.get('adminType')}")
            OutputUtils.info(f"Token expires: {data.get('expiresAt')}")
            
            # Save token to config if requested
            if save_token:
                config.set_bearer_token(token)
                config.save(global_config=True)
                OutputUtils.success("Bearer token saved to config")
            
            if pretty_print:
                OutputUtils.output_json(data)
        else:
            OutputUtils.error(f"Authentication failed: {data.get('message', 'Unknown error')}")
            if pretty_print:
                OutputUtils.output_json(data)
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@auth_group.command('recover')
@click.option('--username', required=True, help='Admin username')
@click.option('--recovery-code', required=True, help='Recovery code (32-digit format: XXXX-XXXX-...)')
@click.option('--save-token', is_flag=True, default=True, help='Save recovery token to config')
@click.pass_context
def admin_recover(ctx, username, recovery_code, save_token):
    """
    Authenticate using recovery code (emergency access when device is lost).
    
    Recovery codes are 32-digit codes in format: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX
    Each code can only be used once. Token is valid for 30 minutes.
    
    After recovery, use 'ezkey admin enrollment reset' to unbind the lost device.
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
        'recoveryCode': recovery_code
    }
    
    url = f"{admin_url}/api/v1/admin/auth/recover"
    OutputUtils.verbose(f"POST {url}", verbose)
    OutputUtils.info(f"Attempting recovery for user: {username}")
    
    response = http_client.post(url, json_data=json_data)
    
    if response.success and response.data:
        data = response.data
        
        if data.get('success') and data.get('recoveryToken'):
            token = data['recoveryToken']
            OutputUtils.success(f"✅ Recovery successful!")
            OutputUtils.warning(f"Recovery token valid for 30 minutes")
            OutputUtils.warning(f"Codes remaining: {data.get('codesRemaining')}")
            OutputUtils.info(f"Token expires: {data.get('expiresAt')}")
            OutputUtils.info("")
            OutputUtils.info("Next steps:")
            OutputUtils.info("1. Use 'ezkey admin enrollment reset --id <enrollment-id>' to unbind lost device")
            OutputUtils.info("2. Bind new device with the new credentials")
            
            # Save token to config if requested
            if save_token:
                config.set_bearer_token(token)
                config.save(global_config=True)
                OutputUtils.success("Recovery token saved to config")
            
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
    
    # Check if we have a token
    if not config.get('bearerToken'):
        OutputUtils.warning("No bearer token found in config")
        return
    
    url = f"{admin_url}/api/v1/admin/auth/logout"
    OutputUtils.verbose(f"POST {url}", verbose)
    OutputUtils.info("Logging out...")
    
    response = http_client.post(url)
    
    if response.success:
        OutputUtils.success("✅ Logout successful")
        
        # Clear token from config
        config.clear_bearer_token()
        config.save(global_config=True)
        OutputUtils.success("Bearer token removed from config")
    else:
        OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)
        
        # Still clear token from config even if logout failed
        config.clear_bearer_token()
        config.save(global_config=True)
        OutputUtils.info("Bearer token removed from config")


# API Keys management commands
@admin_group.group('api-key')
@click.pass_context
def api_key_group(ctx):
    """API key management commands for machine-to-machine authentication."""
    pass


@api_key_group.command('create')
@click.option('--integration-id', required=True, type=int, help='Integration ID for the API key')
@click.option('--description', required=True, help='Description of the API key (e.g., "Production Server")')
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
        'integrationId': integration_id,
        'description': description
    }
    
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
    token = config.get('bearerToken')
    if not token or not token.startswith('ezkey_recovery_'):
        OutputUtils.error("Recovery token required. Use 'ezkey admin auth recover' first.")
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