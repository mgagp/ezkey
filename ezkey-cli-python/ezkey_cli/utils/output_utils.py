"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Output Utilities
Description: Utilities for formatting and displaying CLI output
"""

import sys
from typing import Any

import click
from colorama import Fore, Style, init

from .json_utils import JsonUtils

# Initialize colorama for cross-platform color support
init(autoreset=True)


class OutputUtils:
    """Utilities for CLI output formatting and display."""
    
    @staticmethod
    def success(message: str) -> None:
        """Display a success message."""
        click.echo(f"{Fore.GREEN}✓ {message}{Style.RESET_ALL}")
    
    @staticmethod
    def error(message: str) -> None:
        """Display an error message."""
        click.echo(f"{Fore.RED}✗ Error: {message}{Style.RESET_ALL}", err=True)
    
    @staticmethod
    def warning(message: str) -> None:
        """Display a warning message."""
        click.echo(f"{Fore.YELLOW}⚠ Warning: {message}{Style.RESET_ALL}", err=True)
    
    @staticmethod
    def info(message: str) -> None:
        """Display an info message."""
        click.echo(f"{Fore.BLUE}ℹ {message}{Style.RESET_ALL}")
    
    @staticmethod
    def verbose(message: str, verbose: bool = False) -> None:
        """Display a verbose message if verbose mode is enabled."""
        if verbose:
            click.echo(f"{Fore.CYAN}[VERBOSE] {message}{Style.RESET_ALL}", err=True)
    
    @staticmethod
    def output_response(response: Any, pretty_print: bool = True, verbose: bool = False) -> None:
        """
        Output API response data.
        
        Args:
            response: The response data to output
            pretty_print: Whether to pretty print JSON
            verbose: Whether to show additional details
        """
        from .http_client import ApiResponse
        
        if isinstance(response, ApiResponse):
            if response.success:
                if response.data is not None:
                    click.echo(JsonUtils.format_output(response.data, pretty_print))
                else:
                    OutputUtils.success("Success")
                
                if verbose and response.status:
                    OutputUtils.verbose(f"HTTP Status: {response.status}", verbose=True)
            else:
                OutputUtils.error(response.error or "Unknown error")
                if response.data:
                    click.echo(JsonUtils.format_output(response.data, pretty_print), err=True)
                
                if verbose and response.status:
                    OutputUtils.verbose(f"HTTP Status: {response.status}", verbose=True)
                
                sys.exit(1)
        else:
            # Direct data output
            click.echo(JsonUtils.format_output(response, pretty_print))
    
    @staticmethod
    def confirm(message: str, default: bool = False) -> bool:
        """Prompt user for confirmation."""
        return click.confirm(message, default=default)
    
    @staticmethod
    def prompt(message: str, default: str = None, hide_input: bool = False) -> str:
        """Prompt user for input."""
        return click.prompt(message, default=default, hide_input=hide_input)
    
    @staticmethod
    def echo(message: str, nl: bool = True) -> None:
        """Echo a message to stdout."""
        click.echo(message, nl=nl)