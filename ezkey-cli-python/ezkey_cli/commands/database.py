"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Database Command
Description: Database migration commands integrating with existing flyway functionality
"""

import os
import subprocess
import sys
from pathlib import Path

import click

from ..config import ConfigManager
from ..utils import OutputUtils


@click.group(name='database', invoke_without_command=True)
@click.option('--alias', 'db_alias', is_flag=True, hidden=True, help='Alias for database command')
@click.pass_context
def database_group(ctx, db_alias):
    """Database migration commands using Flyway."""
    if ctx.invoked_subcommand is None:
        click.echo(ctx.get_help())


# Add alias 'db' for convenience  
@click.group(name='db', invoke_without_command=True)
@click.pass_context
def db_alias_group(ctx):
    """Database migration commands using Flyway (alias for database)."""
    if ctx.invoked_subcommand is None:
        click.echo(database_group.get_help(ctx))


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


def run_migration_command(config: ConfigManager, command: str, info: bool = False, 
                         repair: bool = False, verbose: bool = False) -> None:
    """Run migration command using either JAR or script."""
    java_path = config.get('javaPath', 'java')
    ezkey_core_path = config.get('ezkeyCorePath')
    
    project_root = find_project_root()
    
    if ezkey_core_path and Path(ezkey_core_path).exists():
        # Use JAR file directly
        run_with_jar(java_path, ezkey_core_path, command, info, repair, verbose)
    else:
        # Try to use script
        run_with_script(project_root, command, info, repair, verbose)


def run_with_jar(java_path: str, jar_path: str, command: str, info: bool = False,
                repair: bool = False, verbose: bool = False) -> None:
    """Run migration using JAR file directly."""
    cmd = [java_path, '-jar', jar_path]
    
    if info:
        cmd.append('--info')
    elif repair:
        cmd.append('--repair')
    else:
        cmd.append('--migrate')
    
    OutputUtils.verbose(f"Running: {' '.join(cmd)}", verbose)
    
    try:
        result = subprocess.run(cmd, capture_output=False, text=True)
        if result.returncode != 0:
            OutputUtils.error(f"Migration command failed with exit code {result.returncode}")
            sys.exit(result.returncode)
    except FileNotFoundError:
        OutputUtils.error(f"Java executable not found: {java_path}")
        sys.exit(1)
    except Exception as e:
        OutputUtils.error(f"Failed to run migration: {str(e)}")
        sys.exit(1)


def run_with_script(project_root: Path, command: str, info: bool = False,
                   repair: bool = False, verbose: bool = False) -> None:
    """Run migration using existing script."""
    script_path = project_root / 'scripts' / 'ezkey-flyway.sh'
    
    if not script_path.exists():
        OutputUtils.error(
            f"Migration script not found at {script_path}. "
            "Please specify --ezkey-core-jar path to JAR file or run from project root."
        )
        sys.exit(1)
    
    cmd = [str(script_path)]
    
    if info:
        cmd.append('--info')
    elif repair:
        cmd.append('--repair')
    else:
        cmd.append('--migrate')
    
    OutputUtils.verbose(f"Running: {' '.join(cmd)}", verbose)
    
    try:
        # Make script executable
        script_path.chmod(0o755)
        
        result = subprocess.run(cmd, capture_output=False, text=True, cwd=project_root)
        if result.returncode != 0:
            OutputUtils.error(f"Migration script failed with exit code {result.returncode}")
            sys.exit(result.returncode)
    except Exception as e:
        OutputUtils.error(f"Failed to run migration script: {str(e)}")
        sys.exit(1)


@database_group.command('migrate')
@click.option('--java-path', help='Path to Java executable')
@click.option('--ezkey-core-jar', help='Path to ezkey-core JAR file')
@click.option('--info', is_flag=True, help='Show migration info instead of migrating')
@click.option('--repair', is_flag=True, help='Repair the migration metadata table')
@click.pass_context
def migrate(ctx, java_path, ezkey_core_jar, info, repair):
    """Run database migrations."""
    config: ConfigManager = ctx.obj['config']
    verbose = ctx.obj.get('verbose', False)
    
    # Override config with command line options
    if java_path:
        config.set('javaPath', java_path)
    if ezkey_core_jar:
        config.set('ezkeyCorePath', ezkey_core_jar)
    
    run_migration_command(config, 'migrate', info=info, repair=repair, verbose=verbose)


@database_group.command('info')
@click.option('--java-path', help='Path to Java executable')
@click.option('--ezkey-core-jar', help='Path to ezkey-core JAR file')
@click.pass_context
def info_command(ctx, java_path, ezkey_core_jar):
    """Show migration information."""
    config: ConfigManager = ctx.obj['config']
    verbose = ctx.obj.get('verbose', False)
    
    # Override config with command line options
    if java_path:
        config.set('javaPath', java_path)
    if ezkey_core_jar:
        config.set('ezkeyCorePath', ezkey_core_jar)
    
    run_migration_command(config, 'info', info=True, verbose=verbose)


@database_group.command('repair')
@click.option('--java-path', help='Path to Java executable')
@click.option('--ezkey-core-jar', help='Path to ezkey-core JAR file')
@click.pass_context
def repair_command(ctx, java_path, ezkey_core_jar):
    """Repair migration metadata table."""
    config: ConfigManager = ctx.obj['config']
    verbose = ctx.obj.get('verbose', False)
    
    # Override config with command line options
    if java_path:
        config.set('javaPath', java_path)
    if ezkey_core_jar:
        config.set('ezkeyCorePath', ezkey_core_jar)
    
    run_migration_command(config, 'repair', repair=True, verbose=verbose)


# Copy commands to db alias group
@db_alias_group.command('migrate')
@click.option('--java-path', help='Path to Java executable')
@click.option('--ezkey-core-jar', help='Path to ezkey-core JAR file')
@click.option('--info', is_flag=True, help='Show migration info instead of migrating')
@click.option('--repair', is_flag=True, help='Repair the migration metadata table')
@click.pass_context
def db_migrate(ctx, java_path, ezkey_core_jar, info, repair):
    """Run database migrations."""
    # Delegate to main migrate command
    ctx.invoke(migrate, java_path=java_path, ezkey_core_jar=ezkey_core_jar, 
               info=info, repair=repair)


@db_alias_group.command('info')
@click.option('--java-path', help='Path to Java executable')
@click.option('--ezkey-core-jar', help='Path to ezkey-core JAR file')
@click.pass_context
def db_info_command(ctx, java_path, ezkey_core_jar):
    """Show migration information."""
    # Delegate to main info command
    ctx.invoke(info_command, java_path=java_path, ezkey_core_jar=ezkey_core_jar)


@db_alias_group.command('repair')
@click.option('--java-path', help='Path to Java executable')
@click.option('--ezkey-core-jar', help='Path to ezkey-core JAR file')
@click.pass_context
def db_repair_command(ctx, java_path, ezkey_core_jar):
    """Repair migration metadata table."""
    # Delegate to main repair command
    ctx.invoke(repair_command, java_path=java_path, ezkey_core_jar=ezkey_core_jar)


# Make the groups available for import
database = database_group
db = db_alias_group