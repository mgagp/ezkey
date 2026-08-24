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
import {useTranslation} from 'react-i18next';
import {useSaveEnrollment} from './useEnrollments';
import {enrollmentsApi} from '../services/api/enrollments';
import {userFacingAuthApiError} from '../services/api/authApiProblem';
import {fetchVerifiedInstanceInfo} from '../services/api/instanceInfo';
import {BindEnrollmentResponse} from '../services/api/types';
import {cryptoService} from '../services/crypto';
import {DEFAULT_ENROLLMENT_APPROVAL_POLICY} from '../services/security/approvalRequirement';
import {StoredEnrollment} from '../services/storage/enrollmentStorage';
import {env} from '../config/env';
import {readIsDebugBuild} from '../config/buildFlavor';
import {integrationKeyAlgorithmBindError} from '../utils/integrationKeyAlgorithm';
import {buildInstallation, resolveEnrollmentAuthUrl} from '../utils/installationMetadata';
import {deriveLocalEnrollmentId} from '../utils/localEnrollmentIdentity';
import {isControlledEnrollmentBypassAvailable} from '../utils/controlledEnrollmentBypass';
import {logEnrollmentSeedIngest} from '../utils/enrollmentSeedLogRedaction';
import {parseQrPayload} from '../utils/qrPayload';
import {normalizeInstallationId} from '../utils/urlValidation';
import {
  buildBindPayload,
  buildVerifyDevicePayload,
  buildVerifyResultPayload,
} from '../services/crypto/enrollmentPayload';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type EnrollmentDraft = {
  /** Installation-scoped local enrollment handle (storage / Keystore / navigation). */
  id: string;
  /** Auth API enrollment id for this installation (wire bodies). */
  serverEnrollmentId: string;
  /** Normalized Auth URL trust-zone id. */
  installationId: string;
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
      const fallback =
        error instanceof Error
          ? t('enrollmentWizard.requestFailed')
          : t('enrollmentWizard.unexpectedError');
      return userFacingAuthApiError(error, fallback);
    },
    [t],
  );

  const buildDraft = useCallback(
    (
      response: BindEnrollmentResponse,
      request: {enrollmentId: string; enrollmentProofToken: string},
      trustZoneAuthUrl: string | undefined,
    ): EnrollmentDraft | undefined => {
      const rawId = response.enrollmentId ?? request.enrollmentId;
      const serverEnrollmentId = String(rawId);
      const installationId =
        normalizeInstallationId(trustZoneAuthUrl) ??
        resolveEnrollmentAuthUrl(trustZoneAuthUrl);
      if (!installationId) {
        return undefined;
      }
      const localId = deriveLocalEnrollmentId(installationId, serverEnrollmentId);
      return {
        id: localId,
        serverEnrollmentId,
        installationId,
        integrationId: serverEnrollmentId,
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
        const nextDraft = buildDraft(
          response,
          {enrollmentId, enrollmentProofToken},
          urlForBind ?? env.configuredApiBaseUrl,
        );
        if (!nextDraft) {
          setBindError(t('enrollmentWizard.missingAuthUrl'));
          setScannerVisible(false);
          return;
        }
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
    const localEnrollmentId = draft.id;
    const serverEnrollmentId = draft.serverEnrollmentId;
    const now = new Date().toISOString();
    const effectiveAuthUrl = resolveEnrollmentAuthUrl(authUrl);
    setIsSubmitting(true);
    let keyMaterialCreated = false;
    try {
      await cryptoService.ensureEnrollmentKeyPair(localEnrollmentId);
      keyMaterialCreated = true;
      const publicKey = await cryptoService.getPublicKey(localEnrollmentId);
      const devicePrivateKeyStorageTier =
        await cryptoService.getEnrollmentPrivateKeyStorageTier(localEnrollmentId);
      const challengeNum = Number(challengeResponse);
      const serverEnrollmentNumber = Number(serverEnrollmentId);
      const verifyDevicePayload = buildVerifyDevicePayload(
        draft.enrollmentProofToken,
        serverEnrollmentNumber,
        challengeNum,
        publicKey,
      );
      const proofTokenSigned = await cryptoService.sign(localEnrollmentId, verifyDevicePayload);
      const verifyResponse = await enrollmentsApi.verify(
        {
          enrollmentId: serverEnrollmentId,
          devicePublicKey: publicKey,
          enrollmentProofTokenSigned: proofTokenSigned,
          challengeResponse,
          devicePrivateKeyStorageTier,
        },
        effectiveAuthUrl,
      );
      const verifyResultPayload = buildVerifyResultPayload(
        draft.enrollmentProofToken,
        serverEnrollmentNumber,
        'VERIFIED',
        verifyResponse.enrollmentVerifyMessage,
      );
      const verifyResultOk = await cryptoService.verify(
        verifyResultPayload,
        verifyResponse.enrollmentVerifyPayloadSignedByIntegration,
        draft.integrationPublicKey,
      );
      if (!verifyResultOk) {
        await cryptoService.deleteEnrollmentKeyPair(localEnrollmentId).catch(() => {});
        keyMaterialCreated = false;
        setChallengeError(t('enrollmentWizard.invalidEnrollmentResult'));
        setEnrollmentChallenge('');
        return;
      }
      let installation =
        effectiveAuthUrl != null ? buildInstallation(effectiveAuthUrl, undefined, now) : undefined;
      if (effectiveAuthUrl && draft.enrollmentProofToken && draft.integrationPublicKey) {
        const instanceInfo = await fetchVerifiedInstanceInfo({
          authUrl: effectiveAuthUrl,
          enrollmentProofToken: draft.enrollmentProofToken,
          integrationPublicKey: draft.integrationPublicKey,
        });
        if (instanceInfo) {
          installation = buildInstallation(effectiveAuthUrl, instanceInfo, now);
        } else {
          console.warn(
            '[EnrollmentWizard] Signed installation metadata unavailable; keeping host-only branding',
          );
        }
      }
      const record: StoredEnrollment = {
        id: localEnrollmentId,
        integrationId: draft.integrationId,
        integrationName: draft.integrationName,
        tenantName: draft.tenantName,
        tenantId: draft.tenantId,
        tenantDescription: draft.tenantDescription,
        createdAt: now,
        lastActivityAt: now,
        favorited: false,
        enrollmentProofToken: draft.enrollmentProofToken,
        enrollmentId: serverEnrollmentId,
        integrationPublicKey: draft.integrationPublicKey,
        enrollmentName: draft.enrollmentName,
        deviceLabel: draft.deviceLabel,
        approvalPolicy: DEFAULT_ENROLLMENT_APPROVAL_POLICY,
        installation,
      };
      await saveEnrollment.mutateAsync(record);
      keyMaterialCreated = false;
      setDraft(undefined);
      setSeedSource(undefined);
      setEnrollmentChallenge('');
      popToTop();
    } catch (error) {
      if (keyMaterialCreated) {
        await cryptoService.deleteEnrollmentKeyPair(localEnrollmentId).catch(() => {});
      }
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
      try {
        const parsed = parseQrPayload(normalizedValue);
        logEnrollmentSeedIngest({
          source,
          rawValue: value,
          normalizedValue,
          parsed,
          diagnosticsEnabled: __DEV__,
          rawDumpEnabled: env.enrollmentSeedRawDump,
        });
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
        logEnrollmentSeedIngest({
          source,
          rawValue: value,
          normalizedValue,
          parseErrorMessage: rawMessage,
          diagnosticsEnabled: __DEV__,
          rawDumpEnabled: env.enrollmentSeedRawDump,
        });
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
