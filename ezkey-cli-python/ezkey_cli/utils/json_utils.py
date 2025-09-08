"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: JSON Utilities
Description: JSON parsing, formatting and file handling utilities
"""

import json
import os
from pathlib import Path
from typing import Any, Union


class JsonUtils:
    """Utilities for JSON handling and file operations."""
    
    @staticmethod
    def pretty_print(data: Any, indent: int = 2) -> str:
        """Pretty print JSON data."""
        try:
            return json.dumps(data, indent=indent, ensure_ascii=False)
        except (TypeError, ValueError) as e:
            return f"Error: Unable to serialize data to JSON: {str(e)}"
    
    @staticmethod
    def parse(json_string: str) -> Any:
        """Parse JSON data from string, with error handling."""
        try:
            return json.loads(json_string)
        except json.JSONDecodeError as e:
            raise ValueError(f"Invalid JSON: {str(e)}")
    
    @staticmethod
    def load_from_file(file_path: str) -> Any:
        """
        Load JSON data from file.
        Supports both absolute and relative paths.
        """
        try:
            # Resolve relative paths
            path = Path(file_path)
            if not path.is_absolute():
                path = Path.cwd() / file_path
            
            if not path.exists():
                raise FileNotFoundError(f"File not found: {path}")
            
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            return JsonUtils.parse(content)
        except Exception as e:
            raise ValueError(f"Failed to load JSON from file {file_path}: {str(e)}")
    
    @staticmethod
    def process_input(input_value: str) -> Any:
        """
        Process input value - if it starts with @, treat as file path.
        Otherwise, try to parse as JSON string.
        """
        if input_value.startswith('@'):
            # File input
            file_path = input_value[1:]
            return JsonUtils.load_from_file(file_path)
        else:
            # Direct JSON input
            return JsonUtils.parse(input_value)
    
    @staticmethod
    def format_output(data: Any, pretty_print: bool = True) -> str:
        """Format output based on pretty print setting."""
        if pretty_print:
            return JsonUtils.pretty_print(data)
        else:
            return json.dumps(data, ensure_ascii=False)
    
    @staticmethod
    def is_valid_json(string: str) -> bool:
        """Validate if string is valid JSON."""
        try:
            json.loads(string)
            return True
        except json.JSONDecodeError:
            return False
    
    @staticmethod
    def get_value(obj: Any, path: str, default: Any = None) -> Any:
        """Safely extract value from nested object using dot notation."""
        keys = path.split('.')
        current = obj
        
        for key in keys:
            if current is None or not isinstance(current, dict) or key not in current:
                return default
            current = current[key]
        
        return current