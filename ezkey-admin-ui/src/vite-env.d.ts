/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
  /** When 'true', enables demo mode (Fill-demo in create dialogs, Ctrl+click on sidebar). Stripped in production. */
  readonly VITE_DEMO_MODE?: string;
  /** Optional `authUrl` in enrollment QR JSON; mirrors Admin API `ezkey.qr.auth-base-url` when set. */
  readonly VITE_QR_AUTH_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
