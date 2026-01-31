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
from .integration_detail import IntegrationDetailScreen
from .integration_create import CreateIntegrationModal
from .confirmation_modal import ConfirmationModal
from .filter_modal import FilterModal
from .enrollments import EnrollmentsScreen
from .enrollment_detail import EnrollmentDetailScreen
from .enrollment_create import CreateEnrollmentModal
from .enrollment_filter import EnrollmentFilterModal
from .audit_logs import AuditLogsScreen
from .audit_log_filter import AuditLogFilterModal
from .auth_attempts import AuthAttemptsScreen
from .auth_attempt_filter import AuthAttemptFilterModal
from .auth_attempt_detail import AuthAttemptDetailScreen
from .admin_provisioning import AdminProvisioningScreen
from .admin_provisioning_filter import AdminProvisioningFilterModal
from .admin_provisioning_detail import AdminProvisioningDetailScreen
from .reauth import ReAuthScreen
from .quick_reauth import QuickReAuthScreen

__all__ = [
    "AuthScreen",
    "HomeScreen",
    "IntegrationsScreen",
    "IntegrationDetailScreen",
    "CreateIntegrationModal",
    "ConfirmationModal",
    "FilterModal",
    "EnrollmentsScreen",
    "EnrollmentDetailScreen",
    "CreateEnrollmentModal",
    "EnrollmentFilterModal",
    "AuditLogsScreen",
    "AuditLogFilterModal",
    "AuthAttemptsScreen",
    "AuthAttemptFilterModal",
    "AuthAttemptDetailScreen",
    "AdminProvisioningScreen",
    "AdminProvisioningFilterModal",
    "AdminProvisioningDetailScreen",
    "ReAuthScreen",
    "QuickReAuthScreen",
]
