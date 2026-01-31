"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Configuration Manager
Description: Manages CLI configuration with hierarchical overrides (CLI > current dir > home dir)
"""

import json
import os
from pathlib import Path
from typing import Any, Dict, Optional, Union
from datetime import datetime, timezone


class ConfigManager:
    """Manages hierarchical configuration for the ezkey CLI."""

    CONFIG_FILENAME = "ezkey.json"
    HOME_CONFIG_DIR = ".ezkey"

    def __init__(self):
        """Initialize configuration manager with default values."""
        self._config: Dict[str, Any] = {}
        self._load_config()

    def _load_config(self) -> None:
        """
        Load configuration with hierarchical precedence:
        1. Current working directory
        2. Home directory (~/.ezkey/)
        3. Default values
        """
        # Start with default configuration
        self._config = {
            "adminUrl": "http://localhost:9080",
            "authUrl": "http://localhost:8080",
            "cryptoUrl": "http://localhost:9090",
            "prettyPrint": True,
            "timeout": 30000
        }

        # Load from home directory config
        home_config_path = Path.home() / self.HOME_CONFIG_DIR / self.CONFIG_FILENAME
        if home_config_path.exists():
            try:
                with open(home_config_path, 'r', encoding='utf-8') as f:
                    home_config = json.load(f)
                self._config.update(home_config)
            except (json.JSONDecodeError, IOError) as e:
                # Silently ignore errors in home config
                pass

        # Load from current directory config (highest precedence)
        current_config_path = Path.cwd() / self.CONFIG_FILENAME
        if current_config_path.exists():
            try:
                with open(current_config_path, 'r', encoding='utf-8') as f:
                    current_config = json.load(f)
                self._config.update(current_config)
            except (json.JSONDecodeError, IOError) as e:
                # Silently ignore errors in current directory config
                pass

    def get(self, key: str, default: Any = None) -> Any:
        """Get a configuration value by key."""
        return self._config.get(key, default)

    def set(self, key: str, value: Any) -> None:
        """Set a configuration value."""
        self._config[key] = value

    def override(self, overrides: Dict[str, Any]) -> None:
        """Override configuration values (typically from command line)."""
        for key, value in overrides.items():
            if value is not None:
                self._config[key] = value

    def save(self, global_config: bool = False) -> None:
        """
        Save current configuration to file.

        Args:
            global_config: If True, save to home directory. Otherwise, save to current directory.
        """
        if global_config:
            config_dir = Path.home() / self.HOME_CONFIG_DIR
            config_dir.mkdir(exist_ok=True)
            config_path = config_dir / self.CONFIG_FILENAME
        else:
            config_path = Path.cwd() / self.CONFIG_FILENAME

        with open(config_path, 'w', encoding='utf-8') as f:
            json.dump(self._config, f, indent=2)

    def get_all(self) -> Dict[str, Any]:
        """Get all configuration values."""
        return self._config.copy()

    def reset_to_defaults(self) -> None:
        """Reset configuration to default values."""
        self._config = {
            "adminUrl": "http://localhost:9080",
            "authUrl": "http://localhost:8080",
            "cryptoUrl": "http://localhost:9090",
            "prettyPrint": True,
            "timeout": 30000
        }

    def set_bearer_token(self, token: str) -> None:
        """Set bearer token for authentication."""
        self._config['bearerToken'] = token

    def set_token_expires_at(self, expires_at: str) -> None:
        """Set token expiration timestamp (ISO 8601 format)."""
        self._config['tokenExpiresAt'] = expires_at

    def get_token_expires_at(self) -> Optional[str]:
        """Get token expiration timestamp."""
        return self._config.get('tokenExpiresAt')

    def set_admin_type(self, admin_type: str) -> None:
        """Set admin type (e.g., GLOBAL_ADMIN)."""
        self._config['adminType'] = admin_type

    def get_admin_type(self) -> Optional[str]:
        """Get admin type."""
        return self._config.get('adminType')

    def clear_bearer_token(self) -> None:
        """Clear bearer token."""
        if 'bearerToken' in self._config:
            del self._config['bearerToken']

    def set_recovery_token(self, token: str) -> None:
        """Set recovery token for emergency authentication."""
        self._config['recoveryToken'] = token
        # Clear bearer token when setting recovery token (they're mutually exclusive)
        if 'bearerToken' in self._config:
            del self._config['bearerToken']

    def clear_recovery_token(self) -> None:
        """Clear recovery token."""
        if 'recoveryToken' in self._config:
            del self._config['recoveryToken']

    def get_recovery_token(self) -> Optional[str]:
        """Get recovery token if available."""
        return self._config.get('recoveryToken')

    def has_recovery_token(self) -> bool:
        """Check if a recovery token is set."""
        return 'recoveryToken' in self._config and self._config['recoveryToken'] is not None

    def set_api_key(self, integration_key: str, secret_key: str) -> None:
        """Set API key credentials for authentication."""
        self._config['integrationKey'] = integration_key
        self._config['secretKey'] = secret_key

    def clear_api_key(self) -> None:
        """Clear API key credentials."""
        if 'integrationKey' in self._config:
            del self._config['integrationKey']
        if 'secretKey' in self._config:
            del self._config['secretKey']

    def set_admin_username(self, username: str) -> None:
        """Set the admin username (for TUI re-auth and CLI convenience)."""
        self._config['adminUsername'] = username

    def get_admin_username(self) -> Optional[str]:
        """Get the stored admin username."""
        return self._config.get('adminUsername')
        return self._config.get('adminUsername')

    def set_last_auth_time(self) -> None:
        """Record the current time as last successful authentication."""
        self._config['lastAuthTime'] = datetime.now(timezone.utc).isoformat()

    def clear_last_auth_time(self) -> None:
        """Clear last authentication time."""
        if 'lastAuthTime' in self._config:
            del self._config['lastAuthTime']

    def clear_token_expires_at(self) -> None:
        """Clear token expiration timestamp."""
        if 'tokenExpiresAt' in self._config:
            del self._config['tokenExpiresAt']

    def clear_admin_type(self) -> None:
        """Clear admin type."""
        if 'adminType' in self._config:
            del self._config['adminType']

    def is_session_expired(self, timeout_minutes: int = 60) -> bool:
        """
        Check if session has expired based on token expiration or session timeout.

        Args:
            timeout_minutes: Session timeout in minutes (default 60 = 1 hour)

        Returns:
            True if token expired or session timeout exceeded, False if still valid
        """
        # First, check if token has a real expiration time from the API
        expires_at = self._config.get('tokenExpiresAt')
        if expires_at:
            try:
                expiry_time = datetime.fromisoformat(expires_at.replace('Z', '+00:00'))
                if datetime.now(timezone.utc) >= expiry_time:
                    log.debug(f"Token expired at {expires_at}")
                    return True  # Token actually expired
            except (ValueError, TypeError) as e:
                log.warning(f"Could not parse tokenExpiresAt: {e}")

        # Fallback to session timeout (if no real expiration or not expired yet)
        last_auth = self._config.get('lastAuthTime')
        if not last_auth:
            # No previous auth time = first startup after migration or fresh token
            # Initialize it now so next check works
            self.set_last_auth_time()
            return False  # Don't expire on first check

        try:
            last_time = datetime.fromisoformat(last_auth)
            elapsed = (datetime.now(timezone.utc) - last_time).total_seconds()
            return elapsed > (timeout_minutes * 60)
        except (ValueError, TypeError):
            return True  # Invalid format = expired
