import Config from 'react-native-config';

/**
 * When EZKEY_API_BASE_URL is unset: local Auth API (emulator loopback). Enrollment QR JSON
 * includes authUrl when the server sets ezkey.qr.auth-base-url — then that URL is used instead.
 */
const fallbackBaseUrl = 'http://127.0.0.1:8080';
const fallbackTimeoutMs = 10000;

const parseNumber = (value: string | undefined, fallback: number) => {
  if (!value) {
    return fallback;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

const parseText = (value: string | undefined): string | undefined => {
  if (value === undefined) {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
};

/**
 * Parses optional boolean env vars. Accepts: 1 / true / yes (case-insensitive) as true; unset or
 * other values as false.
 */
const parseBool = (value: string | undefined, fallback: boolean) => {
  if (value === undefined || value === '') {
    return fallback;
  }
  const v = value.trim().toLowerCase();
  return v === '1' || v === 'true' || v === 'yes';
};

const resolvedApiBaseUrl = (): string => {
  const raw = Config.EZKEY_API_BASE_URL?.trim();
  return raw && raw.length > 0 ? raw : fallbackBaseUrl;
};

const resolvedConfiguredApiBaseUrl = (): string | undefined => {
  const raw = Config.EZKEY_API_BASE_URL?.trim();
  return raw && raw.length > 0 ? raw : undefined;
};

export const env = {
  configuredApiBaseUrl: resolvedConfiguredApiBaseUrl(),
  apiBaseUrl: resolvedApiBaseUrl(),
  requestTimeoutMs: parseNumber(Config.EZKEY_REQUEST_TIMEOUT, fallbackTimeoutMs),
  /**
   * When true, the Pending authentication error screen shows the technical "Debug (for support)"
   * panel (payload/signature hashes, etc.). Default false — set EZKEY_PENDING_AUTH_DEBUG_PANEL in
   * .env and rebuild native app to enable (react-native-config).
   */
  pendingAuthDebugPanel: parseBool(Config.EZKEY_PENDING_AUTH_DEBUG_PANEL, false),
  /**
   * When true, logs ISO-timestamped steps for the pending-auth **respond** path (Maestro / device
   * diagnosis). Prefix {@code [PendingAuthRespond]}. Default false — set EZKEY_PENDING_AUTH_FLOW_TRACE
   * in .env and rebuild the native app (react-native-config). Does not log challenge digits or tokens.
   *
   * <p><b>Hypothesis-validation strip (removable):</b> remove this flag together with
   * {@code tracePendingAuthRespond} and all its call sites in {@code usePendingAuth.ts} and
   * {@code PendingAuthScreen.tsx}, the {@code EZKEY_PENDING_AUTH_FLOW_TRACE} line in {@code .env.example}, the
   * "Respond-path logging" section in {@code maestro/README.md}, and optional {@code MAESTRO_LOGCAT} handling in
   * {@code scripts/run-real-device-pilot-maestro.sh} when Maestro pilot diagnosis is done.
   */
  pendingAuthFlowTrace: parseBool(Config.EZKEY_PENDING_AUTH_FLOW_TRACE, false),
  /**
   * Controlled enrollment seed bypass (F2a): test harness only.
   *
   * Security posture (all required):
   * - Native **debug** build type ({@code BuildConfig.DEBUG} via {@code readIsDebugBuild()}) —
   *   not React Native {@code __DEV__} alone.
   * - Explicit env enable flag + acknowledgement token at build time.
   * - Payload still processed through the standard bind/verify trust path.
   *
   * Release builds never expose this UI even if a local {@code .env} still has test flags.
   * See {@code docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md}.
   */
  enrollmentSeedBypassEnabled: parseBool(Config.EZKEY_ENROLLMENT_SEED_BYPASS_ENABLED, false),
  enrollmentSeedBypassAck: parseText(Config.EZKEY_ENROLLMENT_SEED_BYPASS_ACK),
  enrollmentSeedBypassQrPayload: parseText(Config.EZKEY_ENROLLMENT_SEED_BYPASS_QR_PAYLOAD),
  /**
   * When true, enrollment seed ingest may log plaintext QR / seed blobs (MOB-004 opt-in).
   * Default false — redacted summaries only. Independent of {@code __DEV__} and of F2a enable.
   * Release preflight forbids true. See {@code docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md}.
   */
  enrollmentSeedRawDump: parseBool(Config.EZKEY_ENROLLMENT_SEED_RAW_DUMP, false),
};
