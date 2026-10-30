"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Utils Package
Description: Utility modules for ezkey CLI
"""

from .http_client import HttpClient, ApiResponse
from .json_utils import JsonUtils
from .output_utils import OutputUtils
from .device_storage import DeviceStorage
from .pagination_utils import (
    build_pagination_params,
    display_page_summary,
    validate_sort_field,
    validate_pagination_options,
    extract_page_content,
    is_paginated_response,
    SORTABLE_FIELDS,
)
from .tls import (
    InsecureTlsNotAllowedError,
    resolve_ssl_verify,
    ssl_verify_from_config,
)
from .secure_file import (
    ApiKeyCredentialFormatError,
    SecureFileExistsError,
    SecureFileParentError,
    SecureFileSymlinkError,
    absolute_path_no_follow,
    ensure_owner_only_dir,
    format_api_key_env,
    prepare_secret_file,
    validate_api_key_credentials,
    write_secret_file,
)

__all__ = [
    "HttpClient",
    "ApiResponse",
    "JsonUtils",
    "OutputUtils",
    "DeviceStorage",
    "build_pagination_params",
    "display_page_summary",
    "validate_sort_field",
    "validate_pagination_options",
    "extract_page_content",
    "is_paginated_response",
    "SORTABLE_FIELDS",
    "InsecureTlsNotAllowedError",
    "resolve_ssl_verify",
    "ssl_verify_from_config",
    "ApiKeyCredentialFormatError",
    "SecureFileExistsError",
    "SecureFileParentError",
    "SecureFileSymlinkError",
    "absolute_path_no_follow",
    "ensure_owner_only_dir",
    "format_api_key_env",
    "prepare_secret_file",
    "validate_api_key_credentials",
    "write_secret_file",
]
