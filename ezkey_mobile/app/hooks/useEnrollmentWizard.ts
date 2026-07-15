/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Hook: useEnrollmentWizard
 * Description: Encapsulates all business logic for the enrollment wizard (BIND → VERIFY phases).
 *              Exposes only the state and callbacks needed by the screen to render and react to user input.
 * Security Context: Drives the enrollment safeguards: QR proof token capture, server signature
 *                   verification (bind), device key generation, challenge enforcement, and
 *                   integration signature verification (verify result). See docs/features/AUTH_SECURITY.md
 *                   and docs/CRYPTO.md.
 * @since 2025
 */

import {useCallback, useState} from 'react';
import {Alert} from 'react-native';
import axios from 'axios';
import {useTranslation} from 'react-i18next';
import {useSaveEnrollment} from './useEnrollments';
import {enrollmentsApi} from '../services/api/enrollments';
import {instanceInfoApi} from '../services/api/instanceInfo';
import {BindEnrollmentResponse} from '../services/api/types';
import {cryptoService} from '../services/crypto';
import {DEFAULT_ENROLLMENT_APPROVAL_POLICY} from '../services/security/approvalRequirement';
import {StoredEnrollment} from '../services/storage/enrollmentStorage';
import {env} from '../config/env';
import {readIsDebugBuild} from '../config/buildFlavor';
import {integrationKeyAlgorithmBindError} from '../utils/integrationKeyAlgorithm';
import {buildInstallation, resolveEnrollmentAuthUrl} from '../utils/installationMetadata';
import {isControlledEnrollmentBypassAvailable} from '../utils/controlledEnrollmentBypass';
import {parseQrPayload} from '../utils/qrPayload';
import {
  buildBindPayload,
  buildVerifyDevicePayload,
  buildVerifyResultPayload,
} from '../services/crypto/enrollmentPayload';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type EnrollmentDraft = {
  id: string;
  integrationId: string;
  integrationName: string;
  tenantName?: string;
  tenantId?: number;
  tenantDescription?: string;
  enrollmentProofToken: string;
  integrationPublicKey: string;
  integrationDescription?: string;
  enrollmentName?: string;
  deviceLabel?: string;
};

type EnrollmentSeedSource = 'camera' | 'controlled-bypass';

const normalizeSeedPayload = (value: string): string => {
  let normalized = value.trim();
  if (
    (normalized.startsWith("'") && normalized.endsWith("'")) ||
    (normalized.startsWith('"') && normalized.endsWith('"'))
  ) {
    normalized = normalized.slice(1, -1).trim();
  }
  normalized = normalized.replace(/\\"/g, '"');

  // Pipe-delimited format: enrollmentId|proofToken|authUrl
  // Used by Maestro runtime flow to avoid JSON quoting issues in PowerShell.
  if (!normalized.startsWith('{') && normalized.includes('|')) {
    const parts = normalized.split('|');
    if (parts.length === 3) {
      const [enrollmentId, enrollmentProofToken, authUrl] = parts.map(p => p.trim());
      normalized = JSON.stringify({enrollmentId, enrollmentProofToken, authUrl});
    }
  }

  return normalized;
};

/**
 * Values returned by the hook that the screen needs to render itself.
 *
 * @since 2025
 */
export type EnrollmentWizardState = {
  // State exposed for rendering
  draft: EnrollmentDraft | undefined;
  isSubmitting: boolean;
  isBinding: boolean;
  bindError: string | undefined;
  cameraError: string | undefined;
  enrollmentChallenge: string;
  challengeError: string | undefined;
  scannerVisible: boolean;
  controlledBypassAvailable: boolean;
  controlledBypassUsed: boolean;
  controlledBypassSeedInput: string;
  // Derived UI helpers
  primaryLabel: string;
  secondaryLabel: string;
  primaryDisabled: boolean;
  secondaryDisabled: boolean;
  hasDraft: boolean;
  // Actions
  setEnrollmentChallenge: (value: string) => void;
  setChallengeError: (value: string | undefined) => void;
  handlePrimary: (hasCameraPermission: boolean, requestPermission: () => Promise<boolean>) => void;
  handleSecondary: () => void;
  handleBack: (goBack: () => void) => void;
  handleQrScanned: (value: string) => void;
  setControlledBypassSeedInput: (value: string) => void;
  handleControlledBypass: () => void;
  handleScannerDismiss: () => void;
};

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

/**
 * Business logic hook for the enrollment wizard screen.
 *
 * Separates state management, API calls, and crypto operations from the
 * presentation layer so that the screen component only handles layout and
 * navigation concerns.
 *
 * @param popToTop Navigation callback to close the wizard on success.
 * @returns All state and action callbacks needed by {@link EnrollmentWizardScreen}.
 * @since 2025
 */
export function useEnrollmentWizard(popToTop: () => void): EnrollmentWizardState {
  const {t} = useTranslation();
  const saveEnrollment = useSaveEnrollment();

  const [draft, setDraft] = useState<EnrollmentDraft | undefined>();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isBinding, setIsBinding] = useState(false);
  const [bindError, setBindError] = useState<string | undefined>();
  const [cameraError, setCameraError] = useState<string | undefined>();
  const [bindForm, setBindForm] = useState({
    enrollmentId: '',
    enrollmentProofToken: '',
  });
  const [enrollmentChallenge, setEnrollmentChallenge] = useState('');
  const [challengeError, setChallengeError] = useState<string | undefined>();
  const [scannerVisible, setScannerVisible] = useState(false);
  const [authUrl, setAuthUrl] = useState<string | undefined>();
  const [seedSource, setSeedSource] = useState<EnrollmentSeedSource | undefined>();
  const [controlledBypassSeedInput, setControlledBypassSeedInput] = useState('');

  // F2a: native debug build type + env enable + ack. Do not use __DEV__ alone — offline debug
  // APKs can ship with __DEV__ false while remaining BuildConfig.DEBUG=true.
  const controlledBypassAvailable = isControlledEnrollmentBypassAvailable({
    enrollmentSeedBypassEnabled: env.enrollmentSeedBypassEnabled,
    enrollmentSeedBypassAck: env.enrollmentSeedBypassAck,
    isDebugBuild: readIsDebugBuild(),
  });

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  const extractErrorMessage = useCallback(
    (error: unknown): string => {
      if (axios.isAxiosError(error)) {
        const data = error.response?.data as Record<string, unknown> | undefined;
        return (
          (typeof data?.message === 'string' ? data.message : null) ??
          (typeof data?.detail === 'string' ? data.detail : null) ??
          (typeof data?.error === 'string' ? data.error : null) ??
          (typeof data?.code === 'string' ? data.code : null) ??
          error.message ??
          t('enrollmentWizard.requestFailed')
        );
      }
      if (error instanceof Error) {
        return error.message;
      }
      return t('enrollmentWizard.unexpectedError');
    },
    [t],
  );

  const buildDraft = useCallback(
    (
      response: BindEnrollmentResponse,
      request: {enrollmentId: string; enrollmentProofToken: string},
    ): EnrollmentDraft => {
      const rawId = response.enrollmentId ?? request.enrollmentId;
      const enrollmentId = String(rawId);
      return {
        id: enrollmentId,
        integrationId: enrollmentId,
        integrationName: response.integrationName ?? t('enrollmentWizard.integrationFallback'),
        tenantName: response.tenantName ?? undefined,
        tenantId: response.tenantId,
        tenantDescription: response.tenantDescription,
        enrollmentProofToken: response.enrollmentProofToken ?? request.enrollmentProofToken,
        integrationPublicKey: response.integrationPublicKey,
        integrationDescription: response.integrationDescription,
        enrollmentName: response.enrollmentName,
        deviceLabel: response.enrollmentName,
      };
    },
    [t],
  );

  const performBinding = useCallback(
    async (override?: {enrollmentId: string; enrollmentProofToken: string; authUrl?: string}) => {
      if (isBinding) {
        return;
      }
      const enrollmentId = (override?.enrollmentId ?? bindForm.enrollmentId).trim();
      const enrollmentProofToken = (
        override?.enrollmentProofToken ?? bindForm.enrollmentProofToken
      ).trim();
      if (!enrollmentId || !enrollmentProofToken) {
        setScannerVisible(false);
        setBindError(t('enrollmentWizard.missingIdOrToken'));
        return;
      }
      const urlForBind =
        (override?.authUrl !== undefined ? override.authUrl : authUrl)?.trim() || undefined;
      const hasGlobalBase = Boolean(env.configuredApiBaseUrl?.trim());
      if (!urlForBind && !hasGlobalBase) {
        setScannerVisible(false);
        setBindError(t('enrollmentWizard.missingAuthUrl'));
        return;
      }
      setBindError(undefined);
      setIsBinding(true);
      setDraft(undefined);
      setEnrollmentChallenge('');
      setChallengeError(undefined);
      try {
        setBindForm(previous => ({
          ...previous,
          enrollmentId,
          enrollmentProofToken,
        }));
        const response = await enrollmentsApi.bind(
          {enrollmentId, enrollmentProofToken},
          urlForBind,
        );
        const algoErr = integrationKeyAlgorithmBindError(response.integrationKeyAlgorithm);
        if (algoErr) {
          setBindError(algoErr);
          setScannerVisible(false);
          return;
        }
        const bindPayload = buildBindPayload(response);
        const bindSigOk = await cryptoService.verify(
          bindPayload,
          response.enrollmentBindPayloadSignedByIntegration,
          response.integrationPublicKey,
        );
        if (!bindSigOk) {
          setBindError(t('enrollmentWizard.invalidServerIdentity'));
          setScannerVisible(false);
          return;
        }
        const nextDraft = buildDraft(response, {enrollmentId, enrollmentProofToken});
        setDraft(nextDraft);
        setScannerVisible(false);
      } catch (error) {
        setBindError(extractErrorMessage(error));
        setScannerVisible(false);
      } finally {
        setIsBinding(false);
      }
    },
    [authUrl, bindForm, buildDraft, extractErrorMessage, isBinding, t],
  );

  const finalizeEnrollment = useCallback(async () => {
    if (!draft) {
      Alert.alert(t('enrollmentWizard.missingScanTitle'), t('enrollmentWizard.missingScanBody'));
      return;
    }
    const challengeResponse = enrollmentChallenge.trim();
    if (challengeResponse.length !== 6) {
      setChallengeError(t('enrollmentWizard.challengeLength'));
      return;
    }
    const enrollmentId = draft.id.toString();
    const now = new Date().toISOString();
    const effectiveAuthUrl = resolveEnrollmentAuthUrl(authUrl);
    setIsSubmitting(true);
    try {
      await cryptoService.ensureEnrollmentKeyPair(enrollmentId);
      const publicKey = await cryptoService.getPublicKey(enrollmentId);
      const devicePrivateKeyStorageTier =
        await cryptoService.getEnrollmentPrivateKeyStorageTier(enrollmentId);
      const challengeNum = Number(challengeResponse);
      const verifyDevicePayload = buildVerifyDevicePayload(
        draft.enrollmentProofToken,
        Number(draft.id),
        challengeNum,
        publicKey,
      );
      const proofTokenSigned = await cryptoService.sign(enrollmentId, verifyDevicePayload);
      const verifyResponse = await enrollmentsApi.verify(
        {
          enrollmentId: draft.id,
          devicePublicKey: publicKey,
          enrollmentProofTokenSigned: proofTokenSigned,
          challengeResponse,
          devicePrivateKeyStorageTier,
        },
        effectiveAuthUrl,
      );
      const verifyResultPayload = buildVerifyResultPayload(
        draft.enrollmentProofToken,
        Number(draft.id),
        'VERIFIED',
        verifyResponse.enrollmentVerifyMessage,
      );
      const verifyResultOk = await cryptoService.verify(
        verifyResultPayload,
        verifyResponse.enrollmentVerifyPayloadSignedByIntegration,
        draft.integrationPublicKey,
      );
      if (!verifyResultOk) {
        setChallengeError(t('enrollmentWizard.invalidEnrollmentResult'));
        setEnrollmentChallenge('');
        return;
      }
      let installation =
        effectiveAuthUrl != null ? buildInstallation(effectiveAuthUrl, undefined, now) : undefined;
      if (effectiveAuthUrl) {
        try {
          const instanceInfo = await instanceInfoApi.get(effectiveAuthUrl);
          installation = buildInstallation(effectiveAuthUrl, instanceInfo, now);
        } catch (error) {
          console.warn('[EnrollmentWizard] Failed to fetch installation metadata:', error);
        }
      }
      const record: StoredEnrollment = {
        id: draft.id,
        integrationId: draft.integrationId,
        integrationName: draft.integrationName,
        tenantName: draft.tenantName,
        tenantId: draft.tenantId,
        tenantDescription: draft.tenantDescription,
        createdAt: now,
        lastActivityAt: now,
        favorited: false,
        enrollmentProofToken: draft.enrollmentProofToken,
        enrollmentId: enrollmentId,
        integrationPublicKey: draft.integrationPublicKey,
        enrollmentName: draft.enrollmentName,
        deviceLabel: draft.deviceLabel,
        approvalPolicy: DEFAULT_ENROLLMENT_APPROVAL_POLICY,
        installation,
      };
      await saveEnrollment.mutateAsync(record);
      setDraft(undefined);
      setSeedSource(undefined);
      setEnrollmentChallenge('');
      popToTop();
    } catch (error) {
      const message = extractErrorMessage(error);
      setChallengeError(message);
      setEnrollmentChallenge('');
    } finally {
      setIsSubmitting(false);
    }
  }, [authUrl, draft, enrollmentChallenge, extractErrorMessage, popToTop, saveEnrollment, t]);

  const localizeQrError = useCallback(
    (message: string) => {
      switch (message) {
        case 'Empty payload':
          return t('enrollmentWizard.qrEmptyPayload');
        case 'Invalid Auth API URL in QR payload.':
          return t('enrollmentWizard.qrInvalidAuthUrl');
        case 'Unsupported QR format':
          return t('enrollmentWizard.qrUnsupportedFormat');
        default:
          return message;
      }
    },
    [t],
  );

  // ---------------------------------------------------------------------------
  // Public action callbacks (consumed by the screen)
  // ---------------------------------------------------------------------------

  const handlePrimary = useCallback(
    (hasCameraPermission: boolean, requestPermission: () => Promise<boolean>) => {
      if (!draft) {
        setBindError(undefined);
        setCameraError(undefined);
        if (hasCameraPermission) {
          setScannerVisible(true);
        } else {
          requestPermission().then(granted => {
            if (granted) {
              setScannerVisible(true);
            } else {
              setCameraError(t('enrollmentWizard.cameraRequired'));
            }
          });
        }
        return;
      }
      if (enrollmentChallenge.trim().length !== 6) {
        setChallengeError(t('enrollmentWizard.challengeLength'));
        return;
      }
      setChallengeError(undefined);
      finalizeEnrollment().catch(() => {});
    },
    [draft, enrollmentChallenge, finalizeEnrollment, t],
  );

  const handleSecondary = useCallback(() => {
    if (draft) {
      if (!isBinding && !isSubmitting) {
        setDraft(undefined);
          setSeedSource(undefined);
        setEnrollmentChallenge('');
        setChallengeError(undefined);
        popToTop();
      }
      return;
    }
    Alert.alert(t('enrollmentWizard.cameraWhyTitle'), t('enrollmentWizard.cameraWhyBody'));
  }, [draft, isBinding, isSubmitting, popToTop, t]);

  const handleBack = useCallback(
    (goBack: () => void) => {
      if (isSubmitting || isBinding) {
        return;
      }
      if (draft) {
        setChallengeError(undefined);
        setEnrollmentChallenge('');
        setDraft(undefined);
        setSeedSource(undefined);
        return;
      }
      goBack();
    },
    [draft, isBinding, isSubmitting],
  );

  const ingestSeedPayload = useCallback(
    (value: string, source: EnrollmentSeedSource) => {
      const normalizedValue = normalizeSeedPayload(value);
      if (__DEV__) {
        console.log('[EnrollmentWizard] Raw QR value:', JSON.stringify(value));
        console.log('[EnrollmentWizard] Normalized QR value:', JSON.stringify(normalizedValue));
      }
      try {
        const parsed = parseQrPayload(normalizedValue);
        if (__DEV__) {
          console.log('[EnrollmentWizard] Parsed QR payload:', JSON.stringify(parsed));
        }
        setSeedSource(source);
        setAuthUrl(parsed.authUrl);
        setBindForm(() => ({
          enrollmentId: parsed.enrollmentId,
          enrollmentProofToken: parsed.enrollmentProofToken,
        }));
        setBindError(undefined);
        performBinding(parsed);
      } catch (error) {
        const rawMessage = error instanceof Error ? error.message : String(error);
        const message = localizeQrError(rawMessage);
        setScannerVisible(false);
        console.warn(
          '[EnrollmentWizard] Invalid QR payload:',
          rawMessage,
          '| raw:',
          JSON.stringify(value),
          '| normalized:',
          JSON.stringify(normalizedValue),
        );
        Alert.alert(
          t('enrollmentWizard.invalidQrTitle'),
          t('enrollmentWizard.invalidQrBody', {details: message}),
        );
      }
    },
    [localizeQrError, performBinding, t],
  );

  const handleQrScanned = useCallback(
    (value: string) => {
      ingestSeedPayload(value, 'camera');
    },
    [ingestSeedPayload],
  );

  const handleControlledBypass = useCallback(() => {
    if (!controlledBypassAvailable) {
      setCameraError(t('enrollmentWizard.controlledBypassDisabled'));
      return;
    }
    const payload = controlledBypassSeedInput.trim() || env.enrollmentSeedBypassQrPayload;
    if (!payload) {
      setBindError(t('enrollmentWizard.controlledBypassMissingPayload'));
      return;
    }
    setCameraError(undefined);
    setBindError(undefined);
    setScannerVisible(false);
    ingestSeedPayload(payload, 'controlled-bypass');
  }, [controlledBypassAvailable, controlledBypassSeedInput, ingestSeedPayload, t]);

  const handleScannerDismiss = useCallback(() => {
    setScannerVisible(false);
  }, []);

  // ---------------------------------------------------------------------------
  // Derived UI helpers
  // ---------------------------------------------------------------------------

  const hasDraft = Boolean(draft);
  const controlledBypassUsed = seedSource === 'controlled-bypass';
  const challengeMissing = hasDraft && enrollmentChallenge.trim().length !== 6;
  const primaryDisabled =
    (hasDraft && isSubmitting) || (!hasDraft && isBinding) || challengeMissing;
  const secondaryDisabled = (hasDraft && isSubmitting) || (!hasDraft && isBinding);
  const primaryLabel = hasDraft
    ? isSubmitting
      ? t('enrollmentWizard.finishing')
      : t('enrollmentWizard.complete')
    : isBinding
      ? t('enrollmentWizard.binding')
      : t('enrollmentWizard.openScanner');
  const secondaryLabel = hasDraft
    ? t('enrollmentWizard.cancel')
    : t('enrollmentWizard.learnMore');

  return {
    draft,
    isSubmitting,
    isBinding,
    bindError,
    cameraError,
    enrollmentChallenge,
    challengeError,
    scannerVisible,
    controlledBypassAvailable,
    controlledBypassUsed,
    controlledBypassSeedInput,
    primaryLabel,
    secondaryLabel,
    primaryDisabled,
    secondaryDisabled,
    hasDraft,
    setEnrollmentChallenge,
    setChallengeError,
    handlePrimary,
    handleSecondary,
    handleBack,
    handleQrScanned,
    setControlledBypassSeedInput,
    handleControlledBypass,
    handleScannerDismiss,
  };
}
