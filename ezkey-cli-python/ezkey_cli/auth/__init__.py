"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

Auth Module: Session and authentication management
Description: Shared authentication layer for CLI and TUI modes
"""

from .auth_manager import AuthManager

__all__ = ["AuthManager"]
