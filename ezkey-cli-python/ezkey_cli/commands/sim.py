"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Sim Command
Description: Simulation API commands for cryptographic operations and testing
"""

import click

from ..config import ConfigManager
from ..utils import HttpClient, JsonUtils, OutputUtils


@click.group(name='sim')
@click.pass_context
def sim_group(ctx):
    """Simulation API commands for cryptographic operations and testing."""
    pass


@sim_group.command('prooftoken')
@click.pass_context
def generate_proof_token(ctx):
    """Generate a cryptographically secure proof token."""
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    sim_url = config.get('simUrl')
    if not sim_url:
        OutputUtils.error("Sim URL not configured. Use 'ezkey configure set --sim-url <url>'")
        return
    
    url = f"{sim_url}/api/v1/sim/prooftoken"
    OutputUtils.verbose(f"POST {url}", verbose)
    
    response = http_client.post(url)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@sim_group.command('keypair')
@click.option('--key-size', default=2048, type=int, help='Key size in bits (1024-4096)')
@click.pass_context
def generate_key_pair(ctx, key_size):
    """Generate RSA key pair."""
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    sim_url = config.get('simUrl')
    if not sim_url:
        OutputUtils.error("Sim URL not configured. Use 'ezkey configure set --sim-url <url>'")
        return
    
    # Validate key size
    if key_size < 1024 or key_size > 4096:
        OutputUtils.error("Key size must be between 1024 and 4096 bits")
        return
    
    url = f"{sim_url}/api/v1/sim/keypair"
    params = {'keySize': key_size}
    
    OutputUtils.verbose(f"POST {url}", verbose)
    OutputUtils.verbose(f"Params: {params}", verbose)
    
    response = http_client.post(url, json_data=params)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@sim_group.command('sign')
@click.option('--data', required=True, help='Data to sign (or @filename for file input)')
@click.option('--private-key', required=True, help='RSA private key (or @filename for file input)')
@click.option('--json', 'json_data', help='JSON data (or @filename for file input)')
@click.pass_context
def sign_data(ctx, data, private_key, json_data):
    """Sign data with RSA private key."""
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    sim_url = config.get('simUrl')
    if not sim_url:
        OutputUtils.error("Sim URL not configured. Use 'ezkey configure set --sim-url <url>'")
        return
    
    # Process inputs
    try:
        # Process data - could be @filename or direct string
        if data.startswith('@'):
            # File input for data
            with open(data[1:], 'r', encoding='utf-8') as f:
                data_value = f.read().strip()
        else:
            data_value = data
        
        # Process private key - could be @filename or direct string
        if private_key.startswith('@'):
            # File input for private key
            with open(private_key[1:], 'r', encoding='utf-8') as f:
                private_key_value = f.read().strip()
        else:
            private_key_value = private_key
        
        # Process JSON data if provided
        request_data = {
            'data': data_value,
            'privateKey': private_key_value
        }
        
        if json_data:
            try:
                extra_data = JsonUtils.process_input(json_data)
                request_data.update(extra_data)
            except Exception as e:
                OutputUtils.error(f"Invalid JSON data: {str(e)}")
                return
        
    except Exception as e:
        OutputUtils.error(f"Failed to process input: {str(e)}")
        return
    
    url = f"{sim_url}/api/v1/sim/sign"
    OutputUtils.verbose(f"POST {url}", verbose)
    if verbose:
        # Don't log private key in verbose mode
        safe_data = {k: v if k != 'privateKey' else '[REDACTED]' 
                    for k, v in request_data.items()}
        OutputUtils.verbose(f"Data: {JsonUtils.format_output(safe_data)}", verbose)
    
    response = http_client.post(url, json_data=request_data)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


@sim_group.command('validate')
@click.option('--data', required=True, help='Original data (or @filename for file input)')
@click.option('--signature', required=True, help='Signature to validate (or @filename for file input)')
@click.option('--public-key', required=True, help='RSA public key (or @filename for file input)')
@click.option('--json', 'json_data', help='JSON data (or @filename for file input)')
@click.pass_context
def validate_signature(ctx, data, signature, public_key, json_data):
    """Validate RSA signature."""
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    sim_url = config.get('simUrl')
    if not sim_url:
        OutputUtils.error("Sim URL not configured. Use 'ezkey configure set --sim-url <url>'")
        return
    
    # Process inputs
    try:
        # Process data - could be @filename or direct string
        if data.startswith('@'):
            with open(data[1:], 'r', encoding='utf-8') as f:
                data_value = f.read().strip()
        else:
            data_value = data
        
        # Process signature - could be @filename or direct string
        if signature.startswith('@'):
            with open(signature[1:], 'r', encoding='utf-8') as f:
                signature_value = f.read().strip()
        else:
            signature_value = signature
        
        # Process public key - could be @filename or direct string
        if public_key.startswith('@'):
            with open(public_key[1:], 'r', encoding='utf-8') as f:
                public_key_value = f.read().strip()
        else:
            public_key_value = public_key
        
        # Build request data
        request_data = {
            'data': data_value,
            'signature': signature_value,
            'publicKey': public_key_value
        }
        
        # Process JSON data if provided
        if json_data:
            try:
                extra_data = JsonUtils.process_input(json_data)
                request_data.update(extra_data)
            except Exception as e:
                OutputUtils.error(f"Invalid JSON data: {str(e)}")
                return
        
    except Exception as e:
        OutputUtils.error(f"Failed to process input: {str(e)}")
        return
    
    url = f"{sim_url}/api/v1/sim/validate"
    OutputUtils.verbose(f"POST {url}", verbose)
    if verbose:
        OutputUtils.verbose(f"Data: {JsonUtils.format_output(request_data)}", verbose)
    
    response = http_client.post(url, json_data=request_data)
    OutputUtils.output_response(response, pretty_print=pretty_print, verbose=verbose)


# Make the group available for import
sim = sim_group