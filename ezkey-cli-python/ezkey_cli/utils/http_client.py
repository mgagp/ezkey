"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: HTTP Client
Description: HTTP client with error handling and response formatting
"""

import requests
from typing import Any, Dict, Optional, Union
from dataclasses import dataclass

from ..config import ConfigManager


@dataclass
class ApiResponse:
    """Standard API response format."""
    success: bool
    data: Optional[Any] = None
    error: Optional[str] = None
    status: Optional[int] = None


class HttpClient:
    """HTTP client with consistent error handling and response formatting."""
    
    # Default timeout for login operations (6 minutes in milliseconds)
    LOGIN_TIMEOUT_MS = 360000
    
    def __init__(self, config: ConfigManager, custom_timeout: Optional[float] = None):
        """
        Initialize HTTP client with configuration.
        
        Args:
            config: Configuration manager instance
            custom_timeout: Optional custom timeout in seconds (overrides config)
        """
        self.config = config
        self.session = requests.Session()
        self.session.headers.update({
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        })
        
        # Set timeout from config or custom timeout
        if custom_timeout is not None:
            self.timeout = custom_timeout
        else:
            timeout = config.get('timeout', 30000)
            self.timeout = timeout / 1000.0  # Convert ms to seconds
        
        # Set up authentication if available
        self._setup_auth()
    
    def _setup_auth(self):
        """Set up authentication headers from config."""
        # Priority order: bearer token > recovery token > API key
        # Bearer token authentication (for admin users - normal login)
        bearer_token = self.config.get('bearerToken')
        if bearer_token:
            self.session.headers.update({
                'Authorization': f'Bearer {bearer_token}'
            })
            return
        
        # Recovery token authentication (for emergency access - limited permissions)
        recovery_token = self.config.get('recoveryToken')
        if recovery_token:
            self.session.headers.update({
                'Authorization': f'Bearer {recovery_token}'
            })
            return
        
        # API key authentication (for machine-to-machine)
        integration_key = self.config.get('integrationKey')
        secret_key = self.config.get('secretKey')
        if integration_key and secret_key:
            import base64
            credentials = f'{integration_key}:{secret_key}'
            encoded = base64.b64encode(credentials.encode('utf-8')).decode('utf-8')
            self.session.headers.update({
                'Authorization': f'Basic {encoded}'
            })
    
    def _format_error(self, response: requests.Response) -> str:
        """Format error message from HTTP response."""
        try:
            error_data = response.json()
            if isinstance(error_data, dict):
                # Try to extract meaningful error message
                message = error_data.get('message', error_data.get('error', 'Unknown error'))
                return f"HTTP {response.status_code}: {message}"
            else:
                return f"HTTP {response.status_code}: {str(error_data)}"
        except (ValueError, KeyError):
            return f"HTTP {response.status_code}: {response.reason}"
    
    def get(self, url: str, params: Optional[Dict[str, Any]] = None) -> ApiResponse:
        """Make a GET request."""
        try:
            response = self.session.get(url, params=params, timeout=self.timeout)
            
            # Try to parse response body regardless of status code
            # (some endpoints return 400 with useful data)
            try:
                response_data = response.json()
            except ValueError:
                response_data = response.text
            
            if response.ok:
                return ApiResponse(
                    success=True,
                    data=response_data,
                    status=response.status_code
                )
            else:
                error_response = ApiResponse(
                    success=False,
                    error=self._format_error(response),
                    status=response.status_code,
                    data=response_data  # Include data even for errors
                )
                # Add helpful message for authentication errors
                if response.status_code == 401 or response.status_code == 403:
                    error_response.error = (
                        f"{error_response.error}\n"
                        f"💡 Your authentication token may have expired. "
                        f"Try: ezkey admin auth login --username admin"
                    )
                return error_response
                
        except requests.exceptions.RequestException as e:
            return ApiResponse(
                success=False,
                error=f"Request failed: {str(e)}"
            )
    
    def post(self, url: str, data: Optional[Union[Dict[str, Any], str]] = None, 
             json_data: Optional[Dict[str, Any]] = None) -> ApiResponse:
        """Make a POST request."""
        try:
            kwargs = {'timeout': self.timeout}
            
            if json_data is not None:
                kwargs['json'] = json_data
            elif data is not None:
                if isinstance(data, str):
                    kwargs['data'] = data
                    self.session.headers.update({'Content-Type': 'application/json'})
                else:
                    kwargs['json'] = data
            
            response = self.session.post(url, **kwargs)
            
            # Try to parse response body regardless of status code
            # (some endpoints return 400 with useful data, like pending challenge)
            try:
                response_data = response.json()
            except ValueError:
                response_data = response.text
            
            if response.ok:
                return ApiResponse(
                    success=True,
                    data=response_data,
                    status=response.status_code
                )
            else:
                # For 400 errors, check if it contains challenge info (pending passwordless)
                # This is a special case where 400 is actually a valid response
                if response.status_code == 400 and isinstance(response_data, dict):
                    # Check if this looks like a pending passwordless response
                    if (response_data.get('status') == 'pending' and 
                        response_data.get('authAttemptId') is not None and
                        response_data.get('challengeCode') is not None):
                        # This is actually a valid pending response, not an error
                        return ApiResponse(
                            success=True,
                            data=response_data,
                            status=response.status_code
                        )
                
                error_response = ApiResponse(
                    success=False,
                    error=self._format_error(response),
                    status=response.status_code,
                    data=response_data  # Include data even for errors
                )
                # Add helpful message for authentication errors
                if response.status_code == 401 or response.status_code == 403:
                    error_response.error = (
                        f"{error_response.error}\n"
                        f"💡 Your authentication token may have expired. "
                        f"Try: ezkey admin auth login --username admin"
                    )
                return error_response
                
        except requests.exceptions.Timeout as e:
            return ApiResponse(
                success=False,
                error=f"Request timeout: {str(e)}"
            )
        except requests.exceptions.RequestException as e:
            return ApiResponse(
                success=False,
                error=f"Request failed: {str(e)}"
            )
    
    def put(self, url: str, data: Optional[Union[Dict[str, Any], str]] = None,
            json_data: Optional[Dict[str, Any]] = None) -> ApiResponse:
        """Make a PUT request."""
        try:
            kwargs = {'timeout': self.timeout}
            
            if json_data is not None:
                kwargs['json'] = json_data
            elif data is not None:
                if isinstance(data, str):
                    kwargs['data'] = data
                    self.session.headers.update({'Content-Type': 'application/json'})
                else:
                    kwargs['json'] = data
            
            response = self.session.put(url, **kwargs)
            
            if response.ok:
                try:
                    data = response.json()
                except ValueError:
                    data = response.text
                
                return ApiResponse(
                    success=True,
                    data=data,
                    status=response.status_code
                )
            else:
                error_response = ApiResponse(
                    success=False,
                    error=self._format_error(response),
                    status=response.status_code
                )
                # Add helpful message for authentication errors
                if response.status_code == 401 or response.status_code == 403:
                    error_response.error = (
                        f"{error_response.error}\n"
                        f"💡 Your authentication token may have expired. "
                        f"Try: ezkey admin auth login --username admin"
                    )
                return error_response
                
        except requests.exceptions.Timeout as e:
            return ApiResponse(
                success=False,
                error=f"Request timeout: {str(e)}"
            )
        except requests.exceptions.RequestException as e:
            return ApiResponse(
                success=False,
                error=f"Request failed: {str(e)}"
            )
    
    def delete(self, url: str) -> ApiResponse:
        """Make a DELETE request."""
        try:
            response = self.session.delete(url, timeout=self.timeout)
            
            if response.ok:
                try:
                    data = response.json()
                except ValueError:
                    data = response.text
                
                return ApiResponse(
                    success=True,
                    data=data,
                    status=response.status_code
                )
            else:
                error_response = ApiResponse(
                    success=False,
                    error=self._format_error(response),
                    status=response.status_code
                )
                # Add helpful message for authentication errors
                if response.status_code == 401 or response.status_code == 403:
                    error_response.error = (
                        f"{error_response.error}\n"
                        f"💡 Your authentication token may have expired. "
                        f"Try: ezkey admin auth login --username admin"
                    )
                return error_response
                
        except requests.exceptions.Timeout as e:
            return ApiResponse(
                success=False,
                error=f"Request timeout: {str(e)}"
            )
        except requests.exceptions.RequestException as e:
            return ApiResponse(
                success=False,
                error=f"Request failed: {str(e)}"
            )