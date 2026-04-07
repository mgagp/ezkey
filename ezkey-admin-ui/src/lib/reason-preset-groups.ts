/**
 * Operation-scoped keys for reason quick-pick presets. Each key maps to
 * `reasonPresets.groups.<group>.options.<key>` in i18n.
 *
 * @see docs/AUDIT_REASON_AND_JUSTIFICATION_UI.md
 */

export type ReasonPresetGroupId =
  | 'api_key_revoke'
  | 'tenant_toggle'
  | 'admin_lifecycle'
  | 'enrollment_revoke'
  | 'enrollment_lifecycle'
  | 'integration_retire'
  | 'integration_bulk'
  | 'integration_delete'
  | 'encryption_key_rotate'
  | 'audit_chain_justification';

const OPTION_KEYS: Record<ReasonPresetGroupId, readonly string[]> = {
  api_key_revoke: ['rotation', 'exposure', 'decommission', 'configChange'],
  tenant_toggle: [
    'maintenance',
    'securityReview',
    'contractEnded',
    'reactivationMaintenance',
    'tenantRestoreRequest',
  ],
  admin_lifecycle: [
    'roleChange',
    'offboardingHr',
    'temporarySuspension',
    'reactivationInvestigation',
    'mistakenDeactivation',
  ],
  enrollment_revoke: [
    'lostDevice',
    'suspectedCompromise',
    'newDevice',
    'duplicateCleanup',
    'userLeft',
  ],
  enrollment_lifecycle: [
    'lockoutInvestigation',
    'reactivationMaintenance',
    'testRemoved',
    'deleteUserRequest',
  ],
  integration_retire: [
    'decommissioned',
    'migration',
    'vendorChange',
    'pilotComplete',
    'consolidation',
  ],
  integration_bulk: [
    'precautionLockdown',
    'incidentResponse',
    'endMaintenance',
    'tenantWideReset',
  ],
  integration_delete: ['permanentRetention', 'exceptionalCleanup', 'archivedUnneeded'],
  encryption_key_rotate: [
    'scheduledPolicy',
    'incidentResponse',
    'complianceReview',
    'keyExposure',
    'infrastructureMaintenance',
  ],
  audit_chain_justification: [
    'retentionCompliance',
    'externalAudit',
    'incidentDocumentation',
    'qaTesting',
    'operationalSignoff',
  ],
};

/**
 * Returns the i18n option keys for a preset group. Empty groups render no quick-pick UI.
 */
export function getReasonPresetOptionKeys(group: ReasonPresetGroupId): readonly string[] {
  return OPTION_KEYS[group];
}
