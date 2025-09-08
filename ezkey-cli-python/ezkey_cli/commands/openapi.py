"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: OpenAPI Command
Description: Commands to refresh OpenAPI specifications for demo applications
"""

import json
from pathlib import Path

import click

from ..config import ConfigManager
from ..utils import HttpClient, OutputUtils


@click.group(name='openapi')
@click.pass_context
def openapi_group(ctx):
    """OpenAPI specification management commands."""
    pass


def find_project_root() -> Path:
    """Find the project root directory by looking for pom.xml."""
    current = Path.cwd()
    
    # Look up the directory tree for pom.xml
    while current != current.parent:
        if (current / 'pom.xml').exists():
            return current
        current = current.parent
    
    # If not found, assume current directory is project root
    return Path.cwd()


def download_and_save_spec(http_client: HttpClient, url: str, target_path: Path, 
                          api_name: str, verbose: bool = False) -> bool:
    """Download OpenAPI spec and save to file."""
    OutputUtils.verbose(f"Downloading {api_name} spec from {url}", verbose)
    
    response = http_client.get(url)
    
    if not response.success:
        OutputUtils.error(f"Failed to download {api_name} spec: {response.error}")
        return False
    
    try:
        # Ensure target directory exists
        target_path.parent.mkdir(parents=True, exist_ok=True)
        
        # Save the spec
        with open(target_path, 'w', encoding='utf-8') as f:
            if isinstance(response.data, dict):
                json.dump(response.data, f, indent=2)
            else:
                f.write(str(response.data))
        
        OutputUtils.success(f"{api_name} spec saved to {target_path}")
        return True
        
    except Exception as e:
        OutputUtils.error(f"Failed to save {api_name} spec: {str(e)}")
        return False


def refresh_admin_spec(config: ConfigManager, project_root: Path, verbose: bool = False) -> bool:
    """Refresh Admin API specification for demo-app-acme."""
    OutputUtils.info("Refreshing Admin API specification for demo-app-acme...")
    
    admin_url = config.get('adminUrl')
    if not admin_url:
        OutputUtils.error("Admin URL not configured. Use 'ezkey configure set --admin-url <url>'")
        return False
    
    http_client = HttpClient(config)
    spec_url = f"{admin_url}/v3/api-docs"
    target_path = project_root / 'ezkey-demo-app-acme' / 'openapi-spec.json'
    
    return download_and_save_spec(http_client, spec_url, target_path, "Admin API", verbose)


def refresh_auth_spec(config: ConfigManager, project_root: Path, verbose: bool = False) -> bool:
    """Refresh Auth API specification for demo-device."""
    OutputUtils.info("Refreshing Auth API specification for demo-device...")
    
    auth_url = config.get('authUrl')
    if not auth_url:
        OutputUtils.error("Auth URL not configured. Use 'ezkey configure set --auth-url <url>'")
        return False
    
    http_client = HttpClient(config)
    spec_url = f"{auth_url}/v3/api-docs"
    target_path = project_root / 'ezkey-demo-device' / 'openapi-spec.json'
    
    return download_and_save_spec(http_client, spec_url, target_path, "Auth API", verbose)


@openapi_group.command('refresh')
@click.option('--all', 'refresh_all', is_flag=True, help='Refresh all demo applications (default)')
@click.option('--app', is_flag=True, help='Refresh only demo-app-acme (admin API)')
@click.option('--device', is_flag=True, help='Refresh only demo-device (auth API)')
@click.pass_context
def refresh_specs(ctx, refresh_all, app, device):
    """Refresh OpenAPI specifications for demo applications."""
    config: ConfigManager = ctx.obj['config']
    verbose = ctx.obj.get('verbose', False)
    
    project_root = find_project_root()
    OutputUtils.verbose(f"Project root: {project_root}", verbose)
    
    # Determine what to refresh
    if not (app or device):
        # Default to all if no specific option given
        refresh_all = True
    
    success = True
    
    if refresh_all or app:
        success &= refresh_admin_spec(config, project_root, verbose)
    
    if refresh_all or device:
        success &= refresh_auth_spec(config, project_root, verbose)
    
    if success:
        OutputUtils.success("OpenAPI specification refresh completed successfully")
    else:
        OutputUtils.error("OpenAPI specification refresh completed with errors")
        exit(1)


# Make the group available for import
openapi = openapi_group