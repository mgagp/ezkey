import type { ReactNode } from 'react';

// ── Tooltip text (short, one sentence) ──────────────────────────────────────

export const ENROLLMENT_STATUS_HELP = {
  CREATED: 'Enrollment created, waiting for device to bind.',
  BOUND: 'Device bound; pending verification.',
  VERIFIED: 'Device verified and active for authentication.',
  INVALID: 'Verification failed; enrollment is permanently invalid.',
  REVOKED: 'Enrollment permanently revoked by an administrator.',
  EXPIRED: 'Enrollment expired past its expiration date.',
} as const;

export const AUTH_ATTEMPT_STATUS_HELP = {
  PENDING: 'Waiting for mobile device to pick up the request.',
  READ: 'Device received the request; waiting for user decision.',
  ACCEPTED: 'User approved the authentication request.',
  REJECTED: 'User denied the authentication request.',
  EXPIRED: 'No response within the allowed time window.',
  INVALID: 'Cryptographic validation failed.',
} as const;

export const CHECKPOINT_TYPE_HELP = {
  REGULAR: 'Automatic checkpoint created by the scheduler.',
  ARCHIVE_SEAL: 'Checkpoints sealed for archival by an operator.',
  GAP_DECLARATION: 'Declared gap covering a period the system was offline.',
} as const;

export const ENCRYPTION_KEY_STATUS_HELP = {
  PRIMARY: 'Active key used for all new encryption operations.',
  ENABLED: 'Key can decrypt existing data but is not used for new encryption.',
  DISABLED: 'Key is retired; all data has been re-encrypted to a newer key.',
  PENDING: 'Key created but not yet promoted or in use.',
} as const;

/** Header tooltips for encryption keys table columns. */
export const ENCRYPTION_KEYS_TABLE_HEADER_HELP = {
  STATUS: 'Primary = used for new encryption; Enabled = decryption only; Disabled = retired.',
  RECORDS: 'Number of records encrypted with this key.',
  PRIMARY_SINCE: 'Date this key was promoted to primary.',
} as const;

export const REENCRYPT_BUTTON_HELP =
  'Migrate records encrypted with this key to the current primary key.';

export const BATCH_STATUS_HELP = {
  PENDING: 'Batch queued for processing.',
  IN_PROGRESS: 'Batch is being processed.',
  PROCESSING: 'Batch is being processed.',
  COMPLETED: 'Re-encryption completed successfully.',
  FAILED: 'Re-encryption failed; use Resume to retry.',
} as const;

export const ENCRYPTION_KEYS_SECTION_HELP = {
  title: 'Encryption keys',
  content: (
    <>
      AES-256 encryption keys protecting sensitive data at rest. The <strong>PRIMARY</strong> key
      encrypts new data. Old keys remain <strong>ENABLED</strong> until all their records are
      re-encrypted.
    </>
  ),
} as const;

export const HMAC_COLUMN_HELP =
  'Hash-based message authentication code; ensures entry tamper-evidence.';

export const DASHBOARD_AUDIT_CHAIN_HELP = {
  UNDECLARED_GAPS: 'Periods with no checkpoints that have not been formally declared.',
  ANCHOR_CHECKPOINT: 'The last checkpoint before the gap; used as the link point.',
} as const;

export const API_KEY_HELP = {
  INTEGRATION_KEY: 'Public identifier for the API key pair; safe to log.',
  IP_WHITELIST: 'Requests restricted to these IP addresses/CIDR ranges.',
  EXPIRING_SOON: 'Key expires within 30 days; plan rotation.',
  REVOKED: 'Key permanently disabled; cannot be reactivated.',
} as const;

export const ENROLLMENT_DETAIL_HELP = {
  PROOF_TOKEN: 'Cryptographic token used by the mobile device to bind to this enrollment.',
  BINDING_CHALLENGE_CODE: 'One-time code the device must enter to complete enrollment.',
} as const;

// ── ContextHelp popover content (Audit Logs — multi-sentence, may include JSX) ─

export interface ContextHelpEntry {
  title: string;
  content: ReactNode;
}

export const AUDIT_CONTEXT_HELP: Record<string, ContextHelpEntry> = {
  integrityLifecycle: {
    title: 'Integrity & Lifecycle',
    content: (
      <>
        Chain checkpoints seal batches of audit entries every 5 minutes. Use <strong>Verification</strong> to check
        chain and entry integrity. Use <strong>Seal Archive</strong> to mark a period for archival; use{' '}
        <strong>Declare Gap</strong> when the system was offline so the chain stays valid.
      </>
    ),
  },
  sealArchive: {
    title: 'Seal Archive',
    content: (
      <>
        Seals a range of checkpoints for archival (e.g. before dropping a DB partition). You can specify a period by
        timestamps or by checkpoint IDs. A pre-flight integrity check runs automatically.
      </>
    ),
  },
  declareGap: {
    title: 'Declare Gap',
    content: (
      <>
        When the system was offline longer than the scheduler lookback (e.g. 60 min), declare the gap so the next
        regular checkpoint can link correctly. Provide the last checkpoint before the outage as the anchor.
      </>
    ),
  },
  checkpointTimeline: {
    title: 'Checkpoint timeline',
    content: (
      <>
        Lists chain checkpoints in time order. Gaps (undeclared holes between checkpoints) are highlighted. Use row
        actions to fill SEAL range or Declare Gap anchor in the dialogs.
      </>
    ),
  },
};
