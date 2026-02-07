"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Main CLI Entry Point
Description: Main command line interface for ezkey
"""

import sys
import click

from .config import ConfigManager
from .commands import admin, auth, configure, database, db, openapi, crypto, device


@click.group(invoke_without_command=True)
@click.option('--admin-url', help='Admin API URL')
@click.option('--auth-url', help='Auth API URL')
@click.option('--crypto-url', help='Crypto API URL')
@click.option('--no-pretty', is_flag=True, help='Disable pretty printing of JSON output')
@click.option('--timeout', type=int, help='Request timeout in milliseconds')
@click.option('--verbose', is_flag=True, help='Enable verbose output')
@click.option('--tui', is_flag=True, help='Start interactive admin console')
@click.version_option(version='1.0.0', prog_name='ezkey')
@click.pass_context
def cli(ctx, admin_url, auth_url, crypto_url, no_pretty, timeout, verbose, tui):
    """
    Ezkey CLI - Command line interface for Ezkey MFA system.

    The CLI follows the pattern: ezkey <api> <object> <action> [options]

    Use 'ezkey <command> --help' for more information on a specific command.

    Use 'ezkey --tui' to start the interactive admin console.
    """
    # Initialize configuration first
    config = ConfigManager()

    # Check if any non-TUI CLI option or argument has been explicitly provided
    has_cli_options = (
        admin_url is not None or
        auth_url is not None or
        crypto_url is not None or
        no_pretty or
        timeout is not None or
        verbose
    )
    
    # Check if there are any arguments passed (beyond the program name)
    # sys.argv[0] is the program name, so if len > 1, there are arguments
    has_arguments = len(sys.argv) > 1

    # Handle TUI mode with proper precedence:
    # 1. Explicit --tui flag takes priority
    # 2. Default TUI only if enabled and NO CLI arguments/options provided
    # 3. Otherwise, use CLI mode
    if tui:
        # Explicit --tui flag: always launch TUI
        from .tui import start_tui
        start_tui(config)
        return
    elif config.is_default_tui() and not has_cli_options and not has_arguments:
        # Default TUI only if:
        # - defaultTUI config is enabled
        # - No CLI options were explicitly provided
        # - No arguments/subcommands were passed
        from .tui import start_tui
        start_tui(config)
        return

    # Override config with command line options
    overrides = {}
    if admin_url is not None:
        overrides['adminUrl'] = admin_url
    if auth_url is not None:
        overrides['authUrl'] = auth_url
    if crypto_url is not None:
        overrides['cryptoUrl'] = crypto_url
    if timeout is not None:
        overrides['timeout'] = timeout
    if no_pretty:
        overrides['prettyPrint'] = False

    config.override(overrides)

    # Set up context
    ctx.ensure_object(dict)
    ctx.obj['config'] = config
    ctx.obj['verbose'] = verbose
    ctx.obj['pretty_print'] = not no_pretty and config.get('prettyPrint', True)

    # Show help if no subcommand is provided
    if ctx.invoked_subcommand is None:
        click.echo(ctx.get_help())
        click.echo("\nExamples:")
        click.echo("  $ ezkey configure --admin-url http://localhost:9080 --auth-url http://localhost:8080")
        click.echo("  $ ezkey admin integration create --name \"Test App\" --logo \"logo.png\"")
        click.echo("  $ ezkey admin integration list")
        click.echo(
            "  $ ezkey auth enrollment bind --enrollment-id 123 "
            "--enrollment-proof-token EZK-ABC123"
        )
        click.echo(
            "  $ ezkey auth auth-attempt respond --auth-attempt-id 789 "
            "--accepted true --auth-attempt-proof-token-signed @signature.txt"
        )
        click.echo("  $ ezkey crypto keypair --key-size 2048")
        click.echo("  $ ezkey device enroll --enrollment-id 456 --enrollment-proof-token EZK-ABC123 --challenge 123456")
        click.echo("  $ ezkey device auth --enrollment-id 456")
        click.echo("  $ ezkey database migrate")
        click.echo("  $ ezkey openapi refresh --all")
        click.echo()
        click.echo("For more help on a specific command:")
        click.echo("  $ ezkey <command> --help")


# Add command groups
cli.add_command(admin)
cli.add_command(auth)
cli.add_command(crypto)
cli.add_command(device)
cli.add_command(database)
cli.add_command(db)  # Alias for database
cli.add_command(openapi)
cli.add_command(configure)


if __name__ == '__main__':
    cli()
