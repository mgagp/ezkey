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
import axios from 'axios';
import {Buffer} from 'buffer';
import {useTranslation} from 'react-i18next';
import {useEnrollmentById, useMarkEnrollmentPendingChecked} from './useEnrollments';
import {authAttemptsApi} from '../services/api/authAttempts';
import {
  buildPendingPayload,
  buildRespondPayload,
  buildRespondResultPayload,
} from '../services/crypto/authAttemptPayload';
import {cryptoService} from '../services/crypto';
import {requiresProtectedApproval} from '../services/security/approvalRequirement';
import {PendingAttempt} from '../services/pendingAuth/types';
import {securityPreferenceStorage} from '../services/storage/securityPreferenceStorage';
import {StoredEnrollment} from '../services/storage/enrollmentStorage';
import {generateProofToken} from '../utils/generateProofToken';
import {sha256HexUtf8} from '../utils/sha256HexUtf8';
import {useEnrollmentStore} from '../state/enrollmentStore';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Number of digits in the auth challenge PIN. Exported so the screen can pass it to PinCodeInput. */
export const AUTH_CHALLENGE_LENGTH = 2;

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
  pendingPayloadBase64?: string;
  pendingPayloadPreview?: string;
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
      if (axios.isAxiosError(error)) {
        const message =
          error.response?.data?.message ??
          error.response?.data?.error ??
          error.message ??
          t('pendingAuth.requestFailed');
        return message;
      }
      if (error instanceof Error) {
        return error.message;
      }
      return t('pendingAuth.unexpectedError');
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
      setDebugInfo({lastStep: 'start', capturedAtIso: debugSnapshotAt});
      const enrollmentKeyId = enrollment.id.toString();
      // Ensure EC P-256 key pair exists for this enrollment
      await cryptoService.ensureEnrollmentKeyPair(enrollmentKeyId);
      setDebugInfo(prev =>
        prev
          ? {...prev, lastStep: 'after_ensure'}
          : {lastStep: 'after_ensure', capturedAtIso: debugSnapshotAt},
      );
      const deviceProofToken = await generateProofToken();
      const deviceProofTokenSigned = await cryptoService.sign(enrollmentKeyId, deviceProofToken);
      const response = await authAttemptsApi.pending(
        {
          enrollmentId: enrollment.id,
          enrollmentProofToken: enrollment.enrollmentProofToken,
          deviceProofToken,
          deviceProofTokenSigned,
        },
        enrollment.installation?.authUrl,
      );

      if (!response) {
        handleNoPendingResult();
        return;
      }
      setDebugInfo(prev =>
        prev
          ? {...prev, lastStep: 'after_pending'}
          : {lastStep: 'after_pending', capturedAtIso: debugSnapshotAt},
      );

      // Verify integration signature over canonical payload (NFC + proofToken|challenge|title|message)
      const integrationPublicKey = enrollment.integrationPublicKey;
      if (!integrationPublicKey) {
        setGlobalError(t('pendingAuth.missingPendingPublicKey'));
        setAttempt(undefined);
        return;
      }
      const pendingPayload = buildPendingPayload(
        response.authAttemptProofToken,
        response.authAttemptChallengeRequired ?? false,
        response.contextTitle,
        response.contextMessage,
      );
      const pendingPayloadSha256Utf8Hex = sha256HexUtf8(pendingPayload);
      const payloadBase64 = Buffer.from(pendingPayload, 'utf8').toString('base64');
      const signature = response.authAttemptProofTokenSignedByIntegration ?? '';
      const signatureSha256Utf8Hex = sha256HexUtf8(signature);
      const integrationPublicKeySha256Utf8Hex = sha256HexUtf8(integrationPublicKey);
      setDebugInfo(prev =>
        prev
          ? {
              ...prev,
              lastStep: 'before_verify',
              integrationPublicKeyLength: integrationPublicKey?.length,
              integrationPublicKeyPrefix: integrationPublicKey?.slice(0, 24) ?? '',
              integrationPublicKeySha256Utf8Hex,
              pendingPayloadLength: pendingPayload.length,
              pendingPayloadSha256Utf8Hex,
              pendingPayloadBase64: payloadBase64,
              pendingPayloadPreview: pendingPayload.slice(0, 180),
              signatureLength: signature.length,
              signatureSha256Utf8Hex,
              signaturePrefix: signature.slice(0, 24),
            }
          : {
              lastStep: 'before_verify',
              capturedAtIso: debugSnapshotAt,
              integrationPublicKeyLength: integrationPublicKey?.length,
              integrationPublicKeyPrefix: integrationPublicKey?.slice(0, 24) ?? '',
              integrationPublicKeySha256Utf8Hex,
              pendingPayloadLength: pendingPayload.length,
              pendingPayloadSha256Utf8Hex,
              pendingPayloadBase64: payloadBase64,
              pendingPayloadPreview: pendingPayload.slice(0, 180),
              signatureLength: signature.length,
              signatureSha256Utf8Hex,
              signaturePrefix: signature.slice(0, 24),
            },
      );
      const signatureValid = await cryptoService.verify(
        pendingPayload,
        response.authAttemptProofTokenSignedByIntegration,
        integrationPublicKey,
      );
      setDebugInfo(prev =>
        prev
          ? {...prev, lastStep: 'after_verify', signatureValid}
          : {lastStep: 'after_verify', signatureValid, capturedAtIso: debugSnapshotAt},
      );
      if (!signatureValid) {
        setGlobalError(t('pendingAuth.invalidPendingSignature'));
        setAttempt(undefined);
        return;
      }

      setAttempt({
        authAttemptId: String(response.authAttemptId),
        authAttemptProofToken: response.authAttemptProofToken,
        authAttemptProofTokenSignedByIntegration:
          response.authAttemptProofTokenSignedByIntegration,
        challengeRequired: response.authAttemptChallengeRequired,
        integrationName: enrollment.integrationName,
        tenantName: enrollment.tenantName,
        createdAt: new Date().toISOString(),
        contextTitle: response.contextTitle,
        contextMessage: response.contextMessage,
      });
      setChallengeInput('');
      setFormError(undefined);
      setDebugInfo(prev =>
        prev
          ? {...prev, lastStep: 'after_verify'}
          : {lastStep: 'after_verify', capturedAtIso: debugSnapshotAt},
      );
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
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional: individual fields used to avoid full object re-comparison; stable identity of nav callbacks managed by the screen
  }, [
    enrollment?.enrollmentProofToken,
    enrollment?.id,
    enrollment?.installation?.authUrl,
    enrollment?.integrationName,
    enrollment?.integrationPublicKey,
    enrollment?.tenantName,
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
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional: individual fields used to avoid full object re-comparison; stable identity of nav callbacks managed by the screen
  }, [enrollment?.id, initialAttempt, isEnrollmentLoading, loadPendingAttempt]);

  // -------------------------------------------------------------------------
  // Respond (approve / deny)
  // -------------------------------------------------------------------------

  const handleRespond = useCallback(
    async (accepted: boolean) => {
      if (!enrollment || !attempt || isProcessing) {
        return;
      }
      if (
        accepted &&
        attempt.challengeRequired &&
        challengeInput.trim().length !== AUTH_CHALLENGE_LENGTH
      ) {
        setFormError(t('pendingAuth.enterChallenge'));
        return;
      }
      setIsProcessing(true);
      setGlobalError(undefined);
      setFormError(undefined);
      try {
        const enrollmentKeyId = enrollment.id.toString();
        await cryptoService.ensureEnrollmentKeyPair(enrollmentKeyId);
        const securityPreference = await securityPreferenceStorage.getSecurityLevel();
        const shouldProtectRespond = requiresProtectedApproval({
          enrollmentApprovalPolicy: enrollment.approvalPolicy,
          securityPreference,
        });
        const respondPayload = buildRespondPayload(attempt.authAttemptProofToken, accepted);
        const proofTokenSigned = await cryptoService.signForRespond(
          enrollmentKeyId,
          respondPayload,
          shouldProtectRespond,
        );
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
          setGlobalError(t('pendingAuth.missingRespondPublicKey'));
          return;
        }
        const respondSig =
          response.authAttemptProofTokenResultSignedByIntegration?.trim() ?? '';
        if (!respondSig) {
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
          setGlobalError(t('pendingAuth.invalidRespondSignature'));
          return;
        }

        const outcome = response.authAttemptResult;
        const title = attempt.contextTitle ?? enrollment.integrationName;
        const message = response.authAttemptMessage ?? attempt.contextMessage;
        const status =
          outcome === 'APPROVED' ? 'approved' : outcome === 'DENIED' ? 'rejected' : 'failed';

        setRecentAuthResult(enrollment.id, {
          status,
          title,
          message:
            status === 'failed' ? message ?? t('pendingAuth.challengeDidNotMatch') : message,
          completedAt: new Date().toISOString(),
        });
        handleReturnToEnrollmentDetail();
      } catch (error) {
        setGlobalError(extractErrorMessage(error));
      } finally {
        setIsProcessing(false);
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
