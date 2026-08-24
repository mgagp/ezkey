/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Hook: usePendingAuth
 * Description: Encapsulates all business logic for the pending authentication screen
 *              (load, verify, approve/deny). Exposes only the state and callbacks needed by the
 *              screen to render and react to user input.
 * Security Context: Drives the polling model, proof token usage, Ed25519 signature verification,
 *                   and read-once semantics described in docs/features/AUTH_SECURITY.md and
 *                   docs/CRYPTO.md. All cryptographic operations are executed here and results
 *                   are surfaced as plain state values to the screen.
 * @since 2025
 */

import {useCallback, useEffect, useRef, useState} from 'react';
import {Keyboard} from 'react-native';
import {useTranslation} from 'react-i18next';
import {useEnrollmentById, useMarkEnrollmentPendingChecked} from './useEnrollments';
import {userFacingAuthApiError} from '../services/api/authApiProblem';
import {authAttemptsApi} from '../services/api/authAttempts';
import {buildRespondPayload, buildRespondResultPayload} from '../services/crypto/authAttemptPayload';
import {cryptoService} from '../services/crypto';
import {requiresProtectedApproval} from '../services/security/approvalRequirement';
import {
  claimPendingAttempt,
  ClaimPendingDiagnostics,
} from '../services/pendingAuth/claimPendingAttempt';
import {PendingAttempt} from '../services/pendingAuth/types';
import {securityPreferenceStorage} from '../services/storage/securityPreferenceStorage';
import type {StoredEnrollment} from '../services/storage/enrollmentStorage';
import {useEnrollmentStore} from '../state/enrollmentStore';
import {env} from '../config/env';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Number of digits in the auth challenge PIN. Exported so the screen can pass it to PinCodeInput. */
export const AUTH_CHALLENGE_LENGTH = 2;

/**
 * Optional respond-path tracing for device / Maestro timing (guarded by {@link env.pendingAuthFlowTrace}).
 * Never logs challenge digits, proof tokens, or signatures — only step names, ids, lengths, and outcomes.
 * <p><b>Hypothesis-validation strip:</b> delete this function and every {@code tracePendingAuthRespond} call
 * in {@code handleRespond} and {@code PendingAuthScreen} respond buttons when pilot logging is no longer needed
 * (also remove {@code env.pendingAuthFlowTrace}).
 *
 * @param step stable machine-readable step id
 * @param detail optional small JSON-serializable fields
 */
export function tracePendingAuthRespond(
  step: string,
  detail?: Readonly<Record<string, string | number | boolean | undefined>>,
): void {
  if (!env.pendingAuthFlowTrace) {
    return;
  }
  const suffix = detail && Object.keys(detail).length > 0 ? ` ${JSON.stringify(detail)}` : '';
  console.log(`[PendingAuthRespond] ${new Date().toISOString()} ${step}${suffix}`);
}

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * On-screen debug snapshot captured when a pending-auth error occurs.
 * No server or file access needed — shown directly on-device for field diagnosis.
 *
 * @since 2025
 */
export type PendingAuthDebugInfo = {
  /** ISO 8601 timestamp when this debug snapshot started (confirms JS bundle / screen code version). */
  capturedAtIso?: string;
  lastStep: string;
  integrationPublicKeyLength?: number;
  integrationPublicKeyPrefix?: string;
  pendingPayloadLength?: number;
  /** SHA-256 hex of pendingPayload UTF-8; compare to Auth API log PENDING_PAYLOAD_DIAG payloadSha256Utf8Hex. */
  pendingPayloadSha256Utf8Hex?: string;
  signatureLength?: number;
  /** SHA-256 hex of integration signature Base64 string (UTF-8); compare PENDING_SIGNATURE_DIAG. */
  signatureSha256Utf8Hex?: string;
  signaturePrefix?: string;
  /** SHA-256 hex of integrationPublicKey string (UTF-8) passed to verify; compare PENDING_INTEGRATION_PUBLIC_KEY_DIAG. */
  integrationPublicKeySha256Utf8Hex?: string;
  signatureValid?: boolean;
  errorMessage?: string;
};

/**
 * Navigation callbacks required by the hook, passed from the screen to keep
 * the hook navigation-agnostic.
 *
 * @since 2025
 */
type PendingAuthNav = {
  /** Returns true if the navigation stack can go back. */
  canGoBack: () => boolean;
  /** Pops the current screen off the stack. */
  goBack: () => void;
  /** Navigates to the EnrollmentDetail screen for the current enrollment. */
  navigateToEnrollmentDetail: () => void;
};

/**
 * All state and callbacks exposed by {@link usePendingAuth}.
 *
 * @since 2025
 */
export type PendingAuthState = {
  enrollment: StoredEnrollment | undefined;
  isEnrollmentLoading: boolean;
  attempt: PendingAttempt | undefined;
  challengeInput: string;
  setChallengeInput: (value: string) => void;
  formError: string | undefined;
  setFormError: (error: string | undefined) => void;
  globalError: string | undefined;
  isProcessing: boolean;
  loading: boolean;
  debugInfo: PendingAuthDebugInfo | undefined;
  /** True when the screen should show the "no pending" empty state. */
  showEmptyState: boolean;
  /** Primary card title: contextTitle if present, otherwise integrationName. */
  primaryTitle: string | undefined;
  /** Secondary card title: integrationName or tenantName when contextTitle is set. */
  secondaryTitle: string | undefined;
  loadPendingAttempt: () => void;
  handleRespond: (accepted: boolean) => Promise<void>;
};

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

/**
 * Manages all business logic for the pending authentication screen.
 *
 * Handles loading the pending attempt (crypto challenge, server signature verification),
 * approve/deny respond flow (sign, submit, verify respond result), error surfacing,
 * auto-navigation on completion, and the on-device debug panel state.
 *
 * @param enrollmentId  Identifier of the enrollment being checked.
 * @param initialAttempt Pre-loaded attempt passed via navigation params (may be undefined).
 * @param nav Navigation callbacks from the host screen.
 * @returns All state and action callbacks needed by {@link PendingAuthScreen}.
 * @since 2025
 */
export function usePendingAuth(
  enrollmentId: string,
  initialAttempt: PendingAttempt | undefined,
  nav: PendingAuthNav,
): PendingAuthState {
  const {t} = useTranslation();
  const {data: enrollment, isLoading: isEnrollmentLoading} = useEnrollmentById(enrollmentId);
  const markEnrollmentPendingChecked = useMarkEnrollmentPendingChecked();
  const setRecentAuthResult = useEnrollmentStore(store => store.setRecentAuthResult);

  const autoLoadEnrollmentRef = useRef<string | undefined>(undefined);

  const [attempt, setAttempt] = useState<PendingAttempt | undefined>(initialAttempt);
  const [challengeInput, setChallengeInput] = useState('');
  const [formError, setFormError] = useState<string | undefined>();
  const [globalError, setGlobalError] = useState<string | undefined>();
  const [isProcessing, setIsProcessing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [debugInfo, setDebugInfo] = useState<PendingAuthDebugInfo | undefined>(undefined);

  // -------------------------------------------------------------------------
  // Error extraction
  // -------------------------------------------------------------------------

  const extractErrorMessage = useCallback(
    (error: unknown) => {
      const nativeCode =
        typeof error === 'object' &&
        error != null &&
        'code' in error &&
        typeof error.code === 'string'
          ? error.code
          : undefined;
      if (nativeCode === 'EZK_AUTH_CANCELLED') {
        return t('pendingAuth.localAuthCancelled');
      }
      if (nativeCode === 'EZK_AUTH_UNAVAILABLE') {
        return t('pendingAuth.localAuthUnavailable');
      }
      if (nativeCode === 'EZK_KEY_INVALIDATED') {
        return t('pendingAuth.localAuthReenroll');
      }
      const fallback =
        error instanceof Error ? t('pendingAuth.requestFailed') : t('pendingAuth.unexpectedError');
      return userFacingAuthApiError(error, fallback);
    },
    [t],
  );

  // -------------------------------------------------------------------------
  // Navigation helpers (internal)
  // -------------------------------------------------------------------------

  const handleNoPendingResult = useCallback(() => {
    setAttempt(undefined);
    setChallengeInput('');
    setFormError(undefined);

    if (initialAttempt && nav.canGoBack()) {
      nav.goBack();
    }
  }, [initialAttempt, nav]);

  const handleReturnToEnrollmentDetail = useCallback(() => {
    if (nav.canGoBack()) {
      nav.goBack();
      return;
    }
    nav.navigateToEnrollmentDetail();
  }, [nav]);

  // -------------------------------------------------------------------------
  // Load pending attempt
  // -------------------------------------------------------------------------

  const loadPendingAttempt = useCallback(async () => {
    if (!enrollment) {
      return;
    }
    setLoading(true);
    setGlobalError(undefined);
    setDebugInfo(undefined);
    try {
      const debugSnapshotAt = new Date().toISOString();
      try {
        await markEnrollmentPendingChecked.mutateAsync({
          id: enrollment.id,
          checkedAt: debugSnapshotAt,
        });
      } catch (storageError) {
        console.warn('[PendingAuth] Failed to persist last verification timestamp:', storageError);
      }

      const applyDiagnostics = (diagnostics?: ClaimPendingDiagnostics) => {
        if (!diagnostics) {
          return {};
        }
        // MOB-004: hashes + lengths + short prefixes only — never full pending payload / proof token.
        return {
          integrationPublicKeyLength: diagnostics.integrationPublicKeyLength,
          integrationPublicKeyPrefix: diagnostics.integrationPublicKeyPrefix,
          integrationPublicKeySha256Utf8Hex: diagnostics.integrationPublicKeySha256Utf8Hex,
          pendingPayloadLength: diagnostics.pendingPayloadLength,
          pendingPayloadSha256Utf8Hex: diagnostics.pendingPayloadSha256Utf8Hex,
          signatureLength: diagnostics.signatureLength,
          signatureSha256Utf8Hex: diagnostics.signatureSha256Utf8Hex,
          signaturePrefix: diagnostics.signaturePrefix,
          signatureValid: diagnostics.signatureValid,
        };
      };

      const result = await claimPendingAttempt(enrollment, {
        checkedAt: debugSnapshotAt,
        onStep: (step, diagnostics) => {
          setDebugInfo(prev => ({
            ...(prev ?? {capturedAtIso: debugSnapshotAt}),
            lastStep: step,
            capturedAtIso: prev?.capturedAtIso ?? debugSnapshotAt,
            ...applyDiagnostics(diagnostics),
          }));
        },
      });

      if (result.kind === 'none') {
        handleNoPendingResult();
        return;
      }

      if (result.kind === 'fail_closed') {
        const failClosedMessage =
          result.reason === 'missing_integration_public_key'
            ? t('pendingAuth.missingPendingPublicKey')
            : result.reason === 'malformed_pending_response'
              ? t('pendingAuth.malformedPendingResponse')
              : t('pendingAuth.invalidPendingSignature');
        setGlobalError(failClosedMessage);
        setAttempt(undefined);
        if (result.diagnostics) {
          setDebugInfo(prev => ({
            ...(prev ?? {capturedAtIso: debugSnapshotAt, lastStep: 'after_verify'}),
            lastStep: prev?.lastStep ?? 'after_verify',
            capturedAtIso: prev?.capturedAtIso ?? debugSnapshotAt,
            ...applyDiagnostics(result.diagnostics),
          }));
        }
        return;
      }

      setAttempt(result.attempt);
      setChallengeInput('');
      setFormError(undefined);
    } catch (error) {
      const msg = extractErrorMessage(error);
      setDebugInfo(prev =>
        prev
          ? {...prev, lastStep: 'catch', errorMessage: msg}
          : {lastStep: 'catch', errorMessage: msg, capturedAtIso: new Date().toISOString()},
      );
      setGlobalError(msg);
    } finally {
      setLoading(false);
    }
  }, [
    enrollment,
    extractErrorMessage,
    handleNoPendingResult,
    markEnrollmentPendingChecked,
    t,
  ]);

  // -------------------------------------------------------------------------
  // Auto-load effect
  // -------------------------------------------------------------------------

  useEffect(() => {
    if (isEnrollmentLoading || !enrollment) {
      return;
    }
    if (initialAttempt) {
      autoLoadEnrollmentRef.current = enrollment.id;
      return;
    }
    if (autoLoadEnrollmentRef.current === enrollment.id) {
      return;
    }
    autoLoadEnrollmentRef.current = enrollment.id;
    loadPendingAttempt();
  }, [enrollment, initialAttempt, isEnrollmentLoading, loadPendingAttempt]);

  // -------------------------------------------------------------------------
  // Respond (approve / deny)
  // -------------------------------------------------------------------------

  const handleRespond = useCallback(
    async (accepted: boolean) => {
      if (!enrollment || !attempt || isProcessing) {
        tracePendingAuthRespond('handleRespond_skipped_preconditions', {
          accepted,
          hasEnrollment: Boolean(enrollment),
          hasAttempt: Boolean(attempt),
          isProcessing,
        });
        return;
      }
      if (
        accepted &&
        attempt.challengeRequired &&
        challengeInput.trim().length !== AUTH_CHALLENGE_LENGTH
      ) {
        tracePendingAuthRespond('handleRespond_skipped_challenge_incomplete', {
          challengeRequired: true,
          challengeLen: challengeInput.trim().length,
          requiredLen: AUTH_CHALLENGE_LENGTH,
        });
        setFormError(t('pendingAuth.enterChallenge'));
        return;
      }
      setIsProcessing(true);
      setGlobalError(undefined);
      setFormError(undefined);
      Keyboard.dismiss();
      tracePendingAuthRespond('handleRespond_try_begin', {
        accepted,
        authAttemptId: attempt.authAttemptId,
        challengeRequired: attempt.challengeRequired,
        challengeLen: challengeInput.trim().length,
      });
      try {
        const enrollmentKeyId = enrollment.id.toString();
        await cryptoService.requireEnrollmentKeyPair(enrollmentKeyId);
        const securityPreference = await securityPreferenceStorage.getSecurityLevel();
        const shouldProtectRespond = requiresProtectedApproval({
          enrollmentApprovalPolicy: enrollment.approvalPolicy,
          securityPreference,
        });
        tracePendingAuthRespond('handleRespond_after_prefs', {
          protectedSigning: shouldProtectRespond,
        });
        const respondPayload = buildRespondPayload(attempt.authAttemptProofToken, accepted);
        const proofTokenSigned = await cryptoService.signForRespond(
          enrollmentKeyId,
          respondPayload,
          shouldProtectRespond,
        );
        tracePendingAuthRespond('handleRespond_after_device_sign', {
          signedPayloadChars: proofTokenSigned.length,
        });
        const response = await authAttemptsApi.respond(
          {
            authAttemptId: attempt.authAttemptId,
            authAttemptAccepted: accepted,
            authAttemptProofTokenSignedByDevice: proofTokenSigned,
            authAttemptChallengeResponse: challengeInput.trim() || undefined,
          },
          enrollment.installation?.authUrl,
        );

        const integrationPublicKey = enrollment.integrationPublicKey;
        if (!integrationPublicKey) {
          tracePendingAuthRespond('handleRespond_abort_missing_integration_public_key', {});
          setGlobalError(t('pendingAuth.missingRespondPublicKey'));
          return;
        }
        const respondSig =
          response.authAttemptProofTokenResultSignedByIntegration?.trim() ?? '';
        if (!respondSig) {
          tracePendingAuthRespond('handleRespond_abort_missing_respond_signature', {
            authAttemptId: response.authAttemptId,
          });
          setGlobalError(t('pendingAuth.missingRespondSignature'));
          return;
        }
        const respondResultPayload = buildRespondResultPayload(
          attempt.authAttemptProofToken,
          response.authAttemptId,
          response.authAttemptResult,
          response.authAttemptMessage,
        );
        const respondSignatureValid = await cryptoService.verify(
          respondResultPayload,
          respondSig,
          integrationPublicKey,
        );
        if (!respondSignatureValid) {
          tracePendingAuthRespond('handleRespond_abort_invalid_respond_signature', {
            authAttemptId: response.authAttemptId,
          });
          setGlobalError(t('pendingAuth.invalidRespondSignature'));
          return;
        }

        const outcome = response.authAttemptResult;
        const title = attempt.contextTitle ?? enrollment.integrationName;
        const message = response.authAttemptMessage ?? attempt.contextMessage;
        const status =
          outcome === 'APPROVED' ? 'approved' : outcome === 'DENIED' ? 'rejected' : 'failed';

        tracePendingAuthRespond('handleRespond_http_verified', {
          authAttemptId: response.authAttemptId,
          outcome,
        });
        setRecentAuthResult(enrollment.id, {
          status,
          title,
          message:
            status === 'failed' ? message ?? t('pendingAuth.challengeDidNotMatch') : message,
          completedAt: new Date().toISOString(),
        });
        tracePendingAuthRespond('handleRespond_navigate_back', {recentAuthStatus: status});
        handleReturnToEnrollmentDetail();
      } catch (error) {
        const msg = extractErrorMessage(error);
        tracePendingAuthRespond('handleRespond_catch', {
          message: msg.length > 240 ? `${msg.slice(0, 240)}...` : msg,
        });
        setGlobalError(msg);
      } finally {
        setIsProcessing(false);
        tracePendingAuthRespond('handleRespond_finally');
      }
    },
    [
      attempt,
      challengeInput,
      enrollment,
      extractErrorMessage,
      handleReturnToEnrollmentDetail,
      isProcessing,
      setRecentAuthResult,
      t,
    ],
  );

  // -------------------------------------------------------------------------
  // Derived state
  // -------------------------------------------------------------------------

  const hasSecureInfo = true; // With Ed25519, keys are always available if root key exists
  const showEmptyState =
    !loading && !attempt && !globalError && !isEnrollmentLoading && hasSecureInfo;
  const primaryTitle = attempt?.contextTitle?.trim() || attempt?.integrationName;
  const secondaryTitle =
    attempt?.contextTitle?.trim() && attempt.integrationName !== attempt.contextTitle.trim()
      ? attempt.integrationName
      : attempt?.tenantName;

  return {
    enrollment,
    isEnrollmentLoading,
    attempt,
    challengeInput,
    setChallengeInput,
    formError,
    setFormError,
    globalError,
    isProcessing,
    loading,
    debugInfo,
    showEmptyState,
    primaryTitle,
    secondaryTitle,
    loadPendingAttempt,
    handleRespond,
  };
}
