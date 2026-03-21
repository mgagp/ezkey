import Config from 'react-native-config';

const fallbackBaseUrl = 'https://goateed-katalina-monsoonal.ngrok-free.dev';
const fallbackTimeoutMs = 10000;

const parseNumber = (value: string | undefined, fallback: number) => {
  if (!value) {
    return fallback;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
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

export const env = {
  apiBaseUrl: Config.EZKEY_API_BASE_URL ?? fallbackBaseUrl,
  requestTimeoutMs: parseNumber(Config.EZKEY_REQUEST_TIMEOUT, fallbackTimeoutMs),
  /**
   * When true, the Pending authentication error screen shows the technical "Debug (for support)"
   * panel (payload/signature hashes, etc.). Default false — set EZKEY_PENDING_AUTH_DEBUG_PANEL in
   * .env and rebuild native app to enable (react-native-config).
   */
  pendingAuthDebugPanel: parseBool(Config.EZKEY_PENDING_AUTH_DEBUG_PANEL, false),
};
