"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Configure Command
Description: Configuration management commands for setting up CLI parameters
"""

import click

from ..config import ConfigManager
from ..utils import JsonUtils, OutputUtils


@click.group(name='configure')
@click.pass_context
def configure_group(ctx):
    """Configuration management commands."""
    pass


@configure_group.command('set')
@click.option('--admin-url', help='Admin API URL')
@click.option('--auth-url', help='Auth API URL')
@click.option('--sim-url', help='Sim API URL')
@click.option('--java-path', help='Path to Java executable')
@click.option('--ezkey-core-path', help='Path to ezkey-core JAR file')
@click.option('--timeout', type=int, help='Request timeout in milliseconds')
@click.option('--pretty-print', type=bool, help='Enable pretty printing (true/false)')
@click.option('--global', 'global_config', is_flag=True, help='Save to global configuration (home directory)')
@click.pass_context
def set_config(ctx, admin_url, auth_url, sim_url, java_path, ezkey_core_path, 
               timeout, pretty_print, global_config):
    """Set configuration values."""
    config: ConfigManager = ctx.obj['config']
    
    # Update configuration with provided values
    updates = {}
    if admin_url is not None:
        updates['adminUrl'] = admin_url
    if auth_url is not None:
        updates['authUrl'] = auth_url
    if sim_url is not None:
        updates['simUrl'] = sim_url
    if java_path is not None:
        updates['javaPath'] = java_path
    if ezkey_core_path is not None:
        updates['ezkeyCorePath'] = ezkey_core_path
    if timeout is not None:
        updates['timeout'] = timeout
    if pretty_print is not None:
        updates['prettyPrint'] = pretty_print
    
    if not updates:
        OutputUtils.error("No configuration values provided")
        return
    
    # Apply updates
    for key, value in updates.items():
        config.set(key, value)
    
    # Save configuration
    try:
        config.save(global_config=global_config)
        location = "global" if global_config else "local"
        OutputUtils.success(f"Configuration saved to {location} config")
        
        # Show what was updated
        for key, value in updates.items():
            OutputUtils.info(f"{key}: {value}")
            
    except Exception as e:
        OutputUtils.error(f"Failed to save configuration: {str(e)}")


@configure_group.command('get')
@click.option('--key', help='Specific configuration key to get')
@click.pass_context
def get_config(ctx, key):
    """Get configuration values."""
    config: ConfigManager = ctx.obj['config']
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    
    if key:
        # Get specific key
        value = config.get(key)
        if value is not None:
            click.echo(f"{key}: {value}")
        else:
            OutputUtils.error(f"Configuration key '{key}' not found")
    else:
        # Get all configuration
        all_config = config.get_all()
        OutputUtils.output_response(all_config, pretty_print=pretty_print, verbose=verbose)


@configure_group.command('reset')
@click.option('--global', 'global_config', is_flag=True, help='Reset global configuration')
@click.confirmation_option(prompt='Are you sure you want to reset configuration to defaults?')
@click.pass_context
def reset_config(ctx, global_config):
    """Reset configuration to default values."""
    config: ConfigManager = ctx.obj['config']
    
    try:
        config.reset_to_defaults()
        config.save(global_config=global_config)
        
        location = "global" if global_config else "local"
        OutputUtils.success(f"Configuration reset to defaults and saved to {location} config")
        
    except Exception as e:
        OutputUtils.error(f"Failed to reset configuration: {str(e)}")


@configure_group.command('interactive')
@click.pass_context
def interactive_config(ctx):
    """Interactive configuration setup."""
    config: ConfigManager = ctx.obj['config']
    
    OutputUtils.info("Interactive Configuration Setup")
    OutputUtils.echo("Press Enter to keep current values, or type new values:")
    OutputUtils.echo("")
    
    # Get current values
    current_admin_url = config.get('adminUrl', 'http://localhost:9080')
    current_auth_url = config.get('authUrl', 'http://localhost:8080')
    current_sim_url = config.get('simUrl', 'http://localhost:8080')
    current_timeout = config.get('timeout', 30000)
    current_pretty_print = config.get('prettyPrint', True)
    
    # Prompt for values
    admin_url = OutputUtils.prompt(f"Admin API URL", default=current_admin_url)
    auth_url = OutputUtils.prompt(f"Auth API URL", default=current_auth_url)
    sim_url = OutputUtils.prompt(f"Sim API URL", default=current_sim_url)
    timeout = click.prompt(f"Request timeout (ms)", default=current_timeout, type=int)
    pretty_print = click.confirm(f"Enable pretty printing", default=current_pretty_print)
    
    global_config = click.confirm("Save as global configuration?", default=False)
    
    # Update configuration
    config.set('adminUrl', admin_url)
    config.set('authUrl', auth_url)
    config.set('simUrl', sim_url)
    config.set('timeout', timeout)
    config.set('prettyPrint', pretty_print)
    
    # Save configuration
    try:
        config.save(global_config=global_config)
        location = "global" if global_config else "local"
        OutputUtils.success(f"Configuration saved to {location} config")
        
    except Exception as e:
        OutputUtils.error(f"Failed to save configuration: {str(e)}")


# Make the group available for import
configure = configure_group