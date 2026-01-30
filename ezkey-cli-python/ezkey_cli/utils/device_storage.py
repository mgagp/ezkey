"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Device Storage
Description: Manages device enrollment data in JSON files
"""

import json
import os
from pathlib import Path
from typing import Dict, List, Optional, Any
from datetime import datetime


class DeviceStorage:
    """Manages device enrollment storage in ~/.ezkey/devices/ directory."""
    
    DEVICES_DIR = Path.home() / ".ezkey" / "devices"
    
    def __init__(self):
        """Initialize device storage and ensure directory exists."""
        self.DEVICES_DIR.mkdir(parents=True, exist_ok=True)
    
    def _get_device_path(self, enrollment_id: int) -> Path:
        """Get path to device file for given enrollment ID."""
        return self.DEVICES_DIR / f"{enrollment_id}.json"
    
    def save_device(self, device_data: Dict[str, Any]) -> None:
        """
        Save device enrollment data to JSON file.
        
        Args:
            device_data: Device enrollment data including enrollmentId
        
        Raises:
            ValueError: If enrollmentId is missing from device_data
        """
        enrollment_id = device_data.get('enrollmentId')
        if enrollment_id is None:
            raise ValueError("enrollmentId is required in device_data")
        
        device_path = self._get_device_path(enrollment_id)
        
        # Add timestamp if not present
        if 'createdAt' not in device_data:
            device_data['createdAt'] = datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%S.%fZ')
        
        # Update modifiedAt timestamp
        device_data['modifiedAt'] = datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%S.%fZ')
        
        with open(device_path, 'w', encoding='utf-8') as f:
            json.dump(device_data, f, indent=2)
    
    def load_device(self, enrollment_id: int) -> Optional[Dict[str, Any]]:
        """
        Load device enrollment data from JSON file.
        
        Args:
            enrollment_id: Enrollment ID to load
        
        Returns:
            Device data dictionary or None if not found
        """
        device_path = self._get_device_path(enrollment_id)
        
        if not device_path.exists():
            return None
        
        try:
            with open(device_path, 'r', encoding='utf-8') as f:
                return json.load(f)
        except (json.JSONDecodeError, IOError):
            return None
    
    def delete_device(self, enrollment_id: int) -> bool:
        """
        Delete device enrollment data.
        
        Args:
            enrollment_id: Enrollment ID to delete
        
        Returns:
            True if deleted, False if not found
        """
        device_path = self._get_device_path(enrollment_id)
        
        if device_path.exists():
            device_path.unlink()
            return True
        return False
    
    def list_devices(self) -> List[Dict[str, Any]]:
        """
        List all enrolled devices.
        
        Returns:
            List of device data dictionaries
        """
        devices = []
        
        for device_file in self.DEVICES_DIR.glob("*.json"):
            try:
                with open(device_file, 'r', encoding='utf-8') as f:
                    device_data = json.load(f)
                    devices.append(device_data)
            except (json.JSONDecodeError, IOError):
                # Skip invalid files
                continue
        
        # Sort by enrollment ID
        devices.sort(key=lambda d: d.get('enrollmentId', 0))
        return devices
    
    def device_exists(self, enrollment_id: int) -> bool:
        """
        Check if device enrollment exists.
        
        Args:
            enrollment_id: Enrollment ID to check
        
        Returns:
            True if device exists, False otherwise
        """
        return self._get_device_path(enrollment_id).exists()
    
    def update_device(self, enrollment_id: int, updates: Dict[str, Any]) -> bool:
        """
        Update existing device data.
        
        Args:
            enrollment_id: Enrollment ID to update
            updates: Dictionary of fields to update
        
        Returns:
            True if updated, False if device not found
        """
        device_data = self.load_device(enrollment_id)
        if device_data is None:
            return False
        
        device_data.update(updates)
        self.save_device(device_data)
        return True
