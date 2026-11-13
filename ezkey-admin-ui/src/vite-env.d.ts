/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
  /**
   * When `'true'`, match Admin API `ezkey.admin.auth.browser-session-cookie-enabled`: send
   * `credentials: 'include'` and do not require `token` in login JSON (HttpOnly cookie on API host).
   */
  readonly VITE_ADMIN_AUTH_USE_HTTP_ONLY_SESSION_COOKIE?: string;
  /** Optional override matching Admin API `ezkey.admin.auth.browser-csrf-cookie-name`. */
  readonly VITE_ADMIN_AUTH_CSRF_COOKIE_NAME?: string;
  /** Optional override matching Admin API `ezkey.admin.auth.browser-csrf-header-name`. */
  readonly VITE_ADMIN_AUTH_CSRF_HEADER_NAME?: string;
  /** When 'true', enables demo mode (Fill-demo in create dialogs, Ctrl+click on sidebar). Stripped in production. */
  readonly VITE_DEMO_MODE?: string;
  /** Optional `authUrl` in enrollment QR JSON; mirrors Admin API `ezkey.qr.auth-base-url` when set. */
  readonly VITE_QR_AUTH_BASE_URL?: string;
  /**
   * Short git SHA stamped at build time for Public alpha chrome.
   * Set explicitly for Docker/CI (`git rev-parse --short=7 HEAD`); Vite also resolves from git when available.
   */
  readonly VITE_GIT_SHA?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
