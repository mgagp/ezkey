"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Admin Command
Description: Admin API commands for integrations, enrollments, and auth attempts
"""

import time
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
    """List all integrations."""
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
    """Get integration by ID."""
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
    """Create a new integration."""
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
    """Delete an integration."""
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
    """List enrollments."""
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
    """Get enrollment by ID."""
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
    """Create a new enrollment."""
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
    """List authentication attempts."""
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
    """Get authentication attempt by ID."""
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
    """Create a new auth attempt."""
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
@click.option('--timeout', default=30, help='Timeout in seconds', type=int)
@click.option('--polling', default=2, help='Polling interval in seconds', type=int)
@click.pass_context
def wait_for_auth_attempt(ctx, id, timeout, polling):
    """Wait for auth attempt completion."""
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return
    
    url = f"{admin_url}/api/v1/auth-attempts/{id}"
    start_time = time.time()
    
    OutputUtils.info(f"Waiting for auth attempt {id} to complete (timeout: {timeout}s)...")
    
    while True:
        elapsed = time.time() - start_time
        if elapsed > timeout:
            OutputUtils.error(f"Timeout after {timeout} seconds")
            return
        
        OutputUtils.verbose(f"Polling {url} (elapsed: {elapsed:.1f}s)", verbose)
        response = http_client.get(url)
        
        if not response.success:
            OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)
            return
        
        # Check if auth attempt is completed
        if response.data and response.data.get('status') in ['APPROVED', 'REJECTED', 'EXPIRED']:
            OutputUtils.success(f"Auth attempt completed with status: {response.data.get('status')}")
            OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)
            return
        
        time.sleep(polling)


# Make the group available for import
admin = admin_group