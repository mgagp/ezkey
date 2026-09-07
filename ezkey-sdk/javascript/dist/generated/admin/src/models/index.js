"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.RetroactiveIntegrityValidationRunResponseTriggerSourceEnum = exports.IntegrityRuptureReconciliationResultCategoryEnum = exports.IntegrityRuptureReconciliationRequestCategoryEnum = exports.IntegrationResponseDtoLifecycleStatusEnum = exports.EntryIntegrityViolationConciliationStatusEnum = exports.EntryIntegrityConciliationSummaryCategoryEnum = exports.EnrollmentResponseDtoDevicePrivateKeyStorageTierEnum = exports.EnrollmentResponseDtoEnrollmentStatusEnum = exports.DeclareAuditChainIncidentRequestRootCauseEnum = exports.DashboardScheduledJobRowDtoLastStatusEnum = exports.DashboardScheduledJobRowDtoJobKeyEnum = exports.DashboardAlertItemDtoStatusEnum = exports.DashboardAlertItemDtoSeverityEnum = exports.DashboardAlertItemDtoAlertTypeEnum = exports.AuthAttemptWaitResponseDtoStatusEnum = exports.AuthAttemptDtoAuthAttemptStatusEnum = exports.AuditLogResponseDtoApiNameEnum = exports.AuditLogResponseDtoEventStatusEnum = exports.AuditLogResponseDtoEventTypeEnum = exports.AuditChainIncidentResponseDtoRootCauseEnum = exports.AuditChainIncidentResponseDtoStatusEnum = exports.AuditChainCheckpointResponseDtoCheckpointTypeEnum = exports.AuditChainCheckpointResponseDtoLifecycleStateEnum = exports.AlertResponseDtoResolutionReasonEnum = exports.AlertResponseDtoStatusEnum = exports.AlertResponseDtoSeverityEnum = exports.AlertResponseDtoAlertTypeEnum = exports.AdminSessionResponseDtoAdminTypeEnum = exports.AdminResponseDtoLifecycleStatusEnum = exports.AdminResponseDtoAdminTypeEnum = exports.AdminLoginResponseDtoAdminTypeEnum = exports.AdminLoginResponseDtoStatusEnum = exports.AdminCreateRequestDtoOnboardingModeEnum = void 0;
/**
 * @export
 */
exports.AdminCreateRequestDtoOnboardingModeEnum = {
    Immediate: 'IMMEDIATE',
    ActivationCode: 'ACTIVATION_CODE'
};
/**
 * @export
 */
exports.AdminLoginResponseDtoStatusEnum = {
    Pending: 'pending',
    Accepted: 'accepted',
    Rejected: 'rejected'
};
/**
 * @export
 */
exports.AdminLoginResponseDtoAdminTypeEnum = {
    GlobalAdmin: 'GLOBAL_ADMIN',
    TenantAdmin: 'TENANT_ADMIN',
    IntegrationAdmin: 'INTEGRATION_ADMIN'
};
/**
 * @export
 */
exports.AdminResponseDtoAdminTypeEnum = {
    GlobalAdmin: 'GLOBAL_ADMIN',
    TenantAdmin: 'TENANT_ADMIN',
    IntegrationAdmin: 'INTEGRATION_ADMIN'
};
/**
 * @export
 */
exports.AdminResponseDtoLifecycleStatusEnum = {
    PendingActivation: 'PENDING_ACTIVATION',
    Active: 'ACTIVE',
    Deactivated: 'DEACTIVATED'
};
/**
 * @export
 */
exports.AdminSessionResponseDtoAdminTypeEnum = {
    GlobalAdmin: 'GLOBAL_ADMIN',
    TenantAdmin: 'TENANT_ADMIN',
    IntegrationAdmin: 'INTEGRATION_ADMIN'
};
/**
 * @export
 */
exports.AlertResponseDtoAlertTypeEnum = {
    ChainGapPending: 'AUDIT_CHAIN_GAP_PENDING',
    ChainHeartbeatStale: 'AUDIT_CHAIN_HEARTBEAT_STALE',
    IntegrityRupture: 'AUDIT_INTEGRITY_RUPTURE'
};
/**
 * @export
 */
exports.AlertResponseDtoSeverityEnum = {
    Info: 'INFO',
    Warning: 'WARNING',
    Critical: 'CRITICAL'
};
/**
 * @export
 */
exports.AlertResponseDtoStatusEnum = {
    Open: 'OPEN',
    Resolved: 'RESOLVED'
};
/**
 * @export
 */
exports.AlertResponseDtoResolutionReasonEnum = {
    GapDeclared: 'GAP_DECLARED',
    HeartbeatRestored: 'HEARTBEAT_RESTORED',
    Manual: 'MANUAL',
    IntegrityRuptureConciliated: 'INTEGRITY_RUPTURE_CONCILIATED'
};
/**
 * @export
 */
exports.AuditChainCheckpointResponseDtoLifecycleStateEnum = {
    Active: 'ACTIVE',
    Sealed: 'SEALED',
    Exported: 'EXPORTED',
    Purgeable: 'PURGEABLE',
    Purged: 'PURGED'
};
/**
 * @export
 */
exports.AuditChainCheckpointResponseDtoCheckpointTypeEnum = {
    Regular: 'REGULAR',
    ArchiveSeal: 'ARCHIVE_SEAL',
    GapDeclaration: 'GAP_DECLARATION',
    ManipulationConciliation: 'MANIPULATION_CONCILIATION'
};
/**
 * @export
 */
exports.AuditChainIncidentResponseDtoStatusEnum = {
    InProgress: 'IN_PROGRESS',
    RecoveredPendingDeclaration: 'RECOVERED_PENDING_DECLARATION',
    Closed: 'CLOSED'
};
/**
 * @export
 */
exports.AuditChainIncidentResponseDtoRootCauseEnum = {
    PlannedSystemUpgrade: 'PLANNED_SYSTEM_UPGRADE',
    AdminApiDown: 'ADMIN_API_DOWN',
    SchedulerFailure: 'SCHEDULER_FAILURE',
    DbUnavailable: 'DB_UNAVAILABLE',
    NetworkPartition: 'NETWORK_PARTITION',
    Misconfiguration: 'MISCONFIGURATION',
    Unknown: 'UNKNOWN'
};
/**
 * @export
 */
exports.AuditLogResponseDtoEventTypeEnum = {
    AdminLogin: 'ADMIN_LOGIN',
    AdminLogout: 'ADMIN_LOGOUT',
    AdminPasswordChange: 'ADMIN_PASSWORD_CHANGE',
    AdminRecoveryUse: 'ADMIN_RECOVERY_USE',
    AdminRecoveryCodesIssued: 'ADMIN_RECOVERY_CODES_ISSUED',
    AdminRecoveryCodesRegenerated: 'ADMIN_RECOVERY_CODES_REGENERATED',
    AdminActivation: 'ADMIN_ACTIVATION',
    AdminActivationCodeReissued: 'ADMIN_ACTIVATION_CODE_REISSUED',
    AdminRecoveryEnrollmentReset: 'ADMIN_RECOVERY_ENROLLMENT_RESET',
    AdminCreated: 'ADMIN_CREATED',
    AdminProfileUpdated: 'ADMIN_PROFILE_UPDATED',
    AdminDeactivated: 'ADMIN_DEACTIVATED',
    AdminActivated: 'ADMIN_ACTIVATED',
    EnrollmentCreated: 'ENROLLMENT_CREATED',
    EnrollmentUpdated: 'ENROLLMENT_UPDATED',
    EnrollmentDeleted: 'ENROLLMENT_DELETED',
    EnrollmentBind: 'ENROLLMENT_BIND',
    EnrollmentVerify: 'ENROLLMENT_VERIFY',
    EnrollmentRevoked: 'ENROLLMENT_REVOKED',
    EnrollmentDeactivated: 'ENROLLMENT_DEACTIVATED',
    EnrollmentReactivated: 'ENROLLMENT_REACTIVATED',
    EnrollmentAuthAttemptBlocked: 'ENROLLMENT_AUTH_ATTEMPT_BLOCKED',
    EnrollmentExpired: 'ENROLLMENT_EXPIRED',
    AuthAttemptCreated: 'AUTH_ATTEMPT_CREATED',
    AuthAttemptPending: 'AUTH_ATTEMPT_PENDING',
    AuthAttemptRespond: 'AUTH_ATTEMPT_RESPOND',
    AuthAttemptCancelled: 'AUTH_ATTEMPT_CANCELLED',
    AuthAttemptExpired: 'AUTH_ATTEMPT_EXPIRED',
    ApiKeyCreated: 'API_KEY_CREATED',
    ApiKeyUpdated: 'API_KEY_UPDATED',
    ApiKeyRevoked: 'API_KEY_REVOKED',
    ApiKeyExpired: 'API_KEY_EXPIRED',
    ApiKeyAuthSuccess: 'API_KEY_AUTH_SUCCESS',
    ApiKeyAuthFailed: 'API_KEY_AUTH_FAILED',
    ApiKeyIpBlocked: 'API_KEY_IP_BLOCKED',
    SystemError: 'SYSTEM_ERROR',
    KeyIntroduced: 'KEY_INTRODUCED',
    KeyPromotedPrimary: 'KEY_PROMOTED_PRIMARY',
    KeyDemoted: 'KEY_DEMOTED',
    KeyDisabled: 'KEY_DISABLED',
    KeysetBackupCreated: 'KEYSET_BACKUP_CREATED',
    ReencryptionStarted: 'REENCRYPTION_STARTED',
    ReencryptionBatchProgress: 'REENCRYPTION_BATCH_PROGRESS',
    ReencryptionCompleted: 'REENCRYPTION_COMPLETED',
    ReencryptionFailed: 'REENCRYPTION_FAILED',
    ReencryptionResumed: 'REENCRYPTION_RESUMED',
    ReencryptionPaused: 'REENCRYPTION_PAUSED',
    IntegrationCreated: 'INTEGRATION_CREATED',
    IntegrationUpdated: 'INTEGRATION_UPDATED',
    IntegrationRetired: 'INTEGRATION_RETIRED',
    IntegrationDeleted: 'INTEGRATION_DELETED',
    TenantCreated: 'TENANT_CREATED',
    TenantUpdated: 'TENANT_UPDATED',
    TenantDeactivated: 'TENANT_DEACTIVATED',
    TenantActivated: 'TENANT_ACTIVATED',
    EvaluatorSelfRegistration: 'EVALUATOR_SELF_REGISTRATION',
    AuditChainArchiveSealed: 'AUDIT_CHAIN_ARCHIVE_SEALED',
    AuditChainArchiveExported: 'AUDIT_CHAIN_ARCHIVE_EXPORTED',
    AuditChainGapDeclared: 'AUDIT_CHAIN_GAP_DECLARED',
    AuditChainIncidentDeclared: 'AUDIT_CHAIN_INCIDENT_DECLARED',
    AuditIntegrityRuptureConciliated: 'AUDIT_INTEGRITY_RUPTURE_CONCILIATED',
    AuditEntryIntegrityConciliated: 'AUDIT_ENTRY_INTEGRITY_CONCILIATED',
    NightlyIntegrityValidationCompleted: 'NIGHTLY_INTEGRITY_VALIDATION_COMPLETED',
    AlertRaised: 'ALERT_RAISED',
    AlertResolved: 'ALERT_RESOLVED'
};
/**
 * @export
 */
exports.AuditLogResponseDtoEventStatusEnum = {
    Success: 'SUCCESS',
    Failure: 'FAILURE',
    Error: 'ERROR'
};
/**
 * @export
 */
exports.AuditLogResponseDtoApiNameEnum = {
    AdminApi: 'ADMIN_API',
    AuthApi: 'AUTH_API',
    IntegrationApi: 'INTEGRATION_API'
};
/**
 * @export
 */
exports.AuthAttemptDtoAuthAttemptStatusEnum = {
    Pending: 'PENDING',
    Read: 'READ',
    Invalid: 'INVALID',
    Rejected: 'REJECTED',
    Accepted: 'ACCEPTED',
    Expired: 'EXPIRED'
};
/**
 * @export
 */
exports.AuthAttemptWaitResponseDtoStatusEnum = {
    Pending: 'PENDING',
    Read: 'READ',
    Invalid: 'INVALID',
    Rejected: 'REJECTED',
    Accepted: 'ACCEPTED'
};
/**
 * @export
 */
exports.DashboardAlertItemDtoAlertTypeEnum = {
    ChainGapPending: 'AUDIT_CHAIN_GAP_PENDING',
    ChainHeartbeatStale: 'AUDIT_CHAIN_HEARTBEAT_STALE',
    IntegrityRupture: 'AUDIT_INTEGRITY_RUPTURE'
};
/**
 * @export
 */
exports.DashboardAlertItemDtoSeverityEnum = {
    Info: 'INFO',
    Warning: 'WARNING',
    Critical: 'CRITICAL'
};
/**
 * @export
 */
exports.DashboardAlertItemDtoStatusEnum = {
    Open: 'OPEN',
    Resolved: 'RESOLVED'
};
/**
 * @export
 */
exports.DashboardScheduledJobRowDtoJobKeyEnum = {
    AuditChainCheckpoint: 'AUDIT_CHAIN_CHECKPOINT',
    NightlyIntegrityValidation: 'NIGHTLY_INTEGRITY_VALIDATION',
    Reencryption: 'REENCRYPTION'
};
/**
 * @export
 */
exports.DashboardScheduledJobRowDtoLastStatusEnum = {
    Success: 'SUCCESS',
    Failed: 'FAILED',
    NeverRun: 'NEVER_RUN'
};
/**
 * @export
 */
exports.DeclareAuditChainIncidentRequestRootCauseEnum = {
    PlannedSystemUpgrade: 'PLANNED_SYSTEM_UPGRADE',
    AdminApiDown: 'ADMIN_API_DOWN',
    SchedulerFailure: 'SCHEDULER_FAILURE',
    DbUnavailable: 'DB_UNAVAILABLE',
    NetworkPartition: 'NETWORK_PARTITION',
    Misconfiguration: 'MISCONFIGURATION',
    Unknown: 'UNKNOWN'
};
/**
 * @export
 */
exports.EnrollmentResponseDtoEnrollmentStatusEnum = {
    Created: 'CREATED',
    Bound: 'BOUND',
    Verified: 'VERIFIED',
    Invalid: 'INVALID',
    Revoked: 'REVOKED',
    Expired: 'EXPIRED'
};
/**
 * @export
 */
exports.EnrollmentResponseDtoDevicePrivateKeyStorageTierEnum = {
    None: 'NONE',
    Standard: 'STANDARD',
    Strong: 'STRONG'
};
/**
 * @export
 */
exports.EntryIntegrityConciliationSummaryCategoryEnum = {
    AccidentalDbaEdit: 'ACCIDENTAL_DBA_EDIT',
    Corruption: 'CORRUPTION',
    InvestigatedBenign: 'INVESTIGATED_BENIGN',
    Other: 'OTHER'
};
/**
 * @export
 */
exports.EntryIntegrityViolationConciliationStatusEnum = {
    None: 'NONE',
    Acknowledged: 'ACKNOWLEDGED',
    ReTamperSuspected: 'RE_TAMPER_SUSPECTED'
};
/**
 * @export
 */
exports.IntegrationResponseDtoLifecycleStatusEnum = {
    Active: 'ACTIVE',
    Retired: 'RETIRED'
};
/**
 * @export
 */
exports.IntegrityRuptureReconciliationRequestCategoryEnum = {
    AccidentalDbaEdit: 'ACCIDENTAL_DBA_EDIT',
    Corruption: 'CORRUPTION',
    InvestigatedBenign: 'INVESTIGATED_BENIGN',
    Other: 'OTHER'
};
/**
 * @export
 */
exports.IntegrityRuptureReconciliationResultCategoryEnum = {
    AccidentalDbaEdit: 'ACCIDENTAL_DBA_EDIT',
    Corruption: 'CORRUPTION',
    InvestigatedBenign: 'INVESTIGATED_BENIGN',
    Other: 'OTHER'
};
/**
 * @export
 */
exports.RetroactiveIntegrityValidationRunResponseTriggerSourceEnum = {
    Scheduled: 'SCHEDULED',
    Operator: 'OPERATOR'
};
//# sourceMappingURL=index.js.map