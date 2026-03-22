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
  /**
   * Lab / demo: show the Respond MITM simulator (toggle that sends JSON decision mismatched to the
   * device signature). Defaults **on** when unset. Falls back to legacy `EZKEY_DEMO_MITM_NARRATIVE_PANEL`
   * if the new key is absent. Set to false to hide. Rebuild native app after .env changes.
   */
  labRespondMitmSimulator: parseBool(
    Config.EZKEY_LAB_RESPOND_MITM_SIMULATOR ?? Config.EZKEY_DEMO_MITM_NARRATIVE_PANEL,
    true,
  ),
};
