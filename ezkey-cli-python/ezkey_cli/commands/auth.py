"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Auth Command
Description: Auth API commands for enrollment and authentication flows
"""

import click

from ..config import ConfigManager
from ..utils import HttpClient, JsonUtils, OutputUtils


@click.group(name='auth')
@click.pass_context
def auth_group(ctx):
    """Auth API commands for enrollment and authentication flows."""
    pass


# Enrollment commands
@auth_group.group('enrollment')
@click.pass_context
def enrollment_group(ctx):
    """Enrollment binding and verification commands."""
    pass


@enrollment_group.command('bind')
@click.option('--enrollment-id', required=True, type=int, help='Enrollment ID')
@click.option('--enrollment-proof-token', required=True, help='Enrollment proof token')
@click.option('--data', help='JSON data (or @filename for file input)')
@click.pass_context
def bind_enrollment(ctx, enrollment_id, enrollment_proof_token, data):
    """Bind device to enrollment using proof token authentication."""
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    auth_url = config.get('authUrl')
    if not auth_url:
        OutputUtils.error("Auth URL not configured. Use 'ezkey configure set --auth-url <url>'")
        return
    
    url = f"{auth_url}/api/v1/enrollments/bind"
    
    # Process data input
    if data:
        try:
            json_data = JsonUtils.process_input(data)
        except Exception as e:
            OutputUtils.error(f"Invalid JSON data: {str(e)}")
            return
    else:
        json_data = {}
    
    json_data.setdefault('enrollmentId', enrollment_id)
    json_data.setdefault('enrollmentProofToken', enrollment_proof_token)
    
    OutputUtils.verbose(f"POST {url}", verbose)
    if verbose:
        safe_data = json_data.copy()
        safe_data['enrollmentProofToken'] = '[REDACTED]'
        OutputUtils.verbose(f"Data: {JsonUtils.format_output(safe_data)}", verbose)
    
    response = http_client.post(url, json_data=json_data)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@enrollment_group.command('verify')
@click.option('--enrollment-id', required=True, type=int, help='Enrollment ID')
@click.option('--challenge-response', required=True, type=int, help='Challenge response code')
@click.option('--device-public-key', required=True, help='Device public key')
@click.option('--enrollment-proof-token-signed', required=True, help='Signed enrollment proof token')
@click.option('--data', help='JSON data (or @filename for file input)')
@click.pass_context
def verify_enrollment(
    ctx,
    enrollment_id,
    challenge_response,
    device_public_key,
    enrollment_proof_token_signed,
    data,
):
    """Verify enrollment completion."""
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    auth_url = config.get('authUrl')
    if not auth_url:
        OutputUtils.error("Auth URL not configured. Use 'ezkey configure set --auth-url <url>'")
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
    json_data.setdefault('enrollmentId', enrollment_id)
    json_data.setdefault('challengeResponse', challenge_response)
    json_data.setdefault('devicePublicKey', device_public_key)
    json_data.setdefault('enrollmentProofTokenSigned', enrollment_proof_token_signed)
    
    url = f"{auth_url}/api/v1/enrollments/verify"
    OutputUtils.verbose(f"POST {url}", verbose)
    if verbose:
        # Don't log sensitive data like keys/tokens in verbose mode
        safe_data = {
            k: v
            if k not in ['devicePublicKey', 'enrollmentProofTokenSigned']
            else '[REDACTED]'
                    for k, v in json_data.items()}
        OutputUtils.verbose(f"Data: {JsonUtils.format_output(safe_data)}", verbose)
    
    response = http_client.post(url, json_data=json_data)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


# Auth attempt commands
@auth_group.group('auth-attempt')
@click.pass_context
def auth_attempt_group(ctx):
    """Authentication attempt handling commands."""
    pass


@auth_attempt_group.command('pending')
@click.option('--enrollment-id', required=True, type=int, help='Enrollment ID')
@click.option('--enrollment-proof-token', required=True, help='Enrollment proof token')
@click.option('--device-proof-token', required=True, help='Device proof token')
@click.option('--device-proof-token-signed', required=True, help='Signed device proof token')
@click.option('--data', help='JSON data (or @filename for file input)')
@click.pass_context
def check_pending(
    ctx,
    enrollment_id,
    enrollment_proof_token,
    device_proof_token,
    device_proof_token_signed,
    data,
):
    """Check for pending authentication requests."""
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    auth_url = config.get('authUrl')
    if not auth_url:
        OutputUtils.error("Auth URL not configured. Use 'ezkey configure set --auth-url <url>'")
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
    json_data.setdefault('enrollmentId', enrollment_id)
    json_data.setdefault('enrollmentProofToken', enrollment_proof_token)
    json_data.setdefault('deviceProofToken', device_proof_token)
    json_data.setdefault('deviceProofTokenSigned', device_proof_token_signed)
    
    url = f"{auth_url}/api/v1/auth-attempts/pending"
    OutputUtils.verbose(f"POST {url}", verbose)
    if verbose:
        # Don't log sensitive data like tokens in verbose mode
        safe_data = {
            k: v
            if k
            not in ['enrollmentProofToken', 'deviceProofToken', 'deviceProofTokenSigned']
            else '[REDACTED]'
                    for k, v in json_data.items()}
        OutputUtils.verbose(f"Data: {JsonUtils.format_output(safe_data)}", verbose)
    
    response = http_client.post(url, json_data=json_data)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@auth_attempt_group.command('respond')
@click.option('--auth-attempt-id', required=True, type=int, help='Auth attempt ID')
@click.option(
    '--accepted',
    required=True,
    type=click.Choice(['true', 'false'], case_sensitive=False),
    help='Accept or deny the authentication request',
)
@click.option(
    '--auth-attempt-proof-token-signed',
    required=True,
    help='Signed auth attempt proof token',
)
@click.option('--challenge-response', type=int, help='Challenge response (if required)')
@click.option('--data', help='JSON data (or @filename for file input)')
@click.pass_context
def respond_to_auth_attempt(
    ctx,
    auth_attempt_id,
    accepted,
    auth_attempt_proof_token_signed,
    challenge_response,
    data,
):
    """Respond to authentication attempt."""
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    auth_url = config.get('authUrl')
    if not auth_url:
        OutputUtils.error("Auth URL not configured. Use 'ezkey configure set --auth-url <url>'")
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
    json_data.setdefault('authAttemptId', auth_attempt_id)
    if 'authAttemptAccepted' not in json_data:
        json_data['authAttemptAccepted'] = accepted.lower() == 'true'
    if 'authAttemptProofTokenSignedByDevice' not in json_data:
        json_data['authAttemptProofTokenSignedByDevice'] = auth_attempt_proof_token_signed
    
    # Add optional challenge response
    if challenge_response is not None:
        json_data.setdefault('authAttemptChallengeResponse', challenge_response)
    
    url = f"{auth_url}/api/v1/auth-attempts/respond"
    OutputUtils.verbose(f"POST {url}", verbose)
    if verbose:
        # Don't log sensitive data like tokens in verbose mode
        safe_data = {
            k: v
            if k not in ['authAttemptProofTokenSignedByDevice']
            else '[REDACTED]'
            for k, v in json_data.items()}
        OutputUtils.verbose(f"Data: {JsonUtils.format_output(safe_data)}", verbose)
    
    response = http_client.post(url, json_data=json_data)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


# Make the group available for import
auth = auth_group