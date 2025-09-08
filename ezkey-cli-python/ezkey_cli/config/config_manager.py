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
            "simUrl": "http://localhost:8080",
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
            "simUrl": "http://localhost:8080",
            "prettyPrint": True,
            "timeout": 30000
        }