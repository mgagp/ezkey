"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

TUI Module: Screens package
Description: Textual screens for admin console
"""

from .auth import AuthScreen
from .home import HomeScreen
from .integrations import IntegrationsScreen

__all__ = ["AuthScreen", "HomeScreen", "IntegrationsScreen"]
