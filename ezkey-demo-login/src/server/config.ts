// Demo Login — configuration from environment variables (see CONFIGURATION.md).

export interface DemoLoginConfig {
  port: number;
  dbPath: string;
  staticDir: string;
  integrationApiUrl: string;
  integrationKey: string | null;
  secretKey: string | null;
  authTtlSeconds: number;
  sessionHours: number;
  cookieSecure: boolean;
  trustProxy: boolean;
  loginRateLimitPerMinute: number;
}

function int(name: string, fallback: number, min: number, max: number): number {
  const raw = process.env[name];
  if (raw == null || raw.trim() === '') return fallback;
  const value = Number.parseInt(raw, 10);
  if (!Number.isFinite(value) || value < min || value > max) {
    throw new Error(`${name} must be an integer between ${min} and ${max} (got "${raw}")`);
  }
  return value;
}

function bool(name: string, fallback: boolean): boolean {
  const raw = process.env[name]?.trim().toLowerCase();
  if (!raw) return fallback;
  if (raw === 'true' || raw === '1' || raw === 'yes') return true;
  if (raw === 'false' || raw === '0' || raw === 'no') return false;
  throw new Error(`${name} must be true or false (got "${raw}")`);
}

function text(name: string): string | null {
  const raw = process.env[name]?.trim();
  return raw ? raw : null;
}

export function loadConfig(): DemoLoginConfig {
  return {
    port: int('DEMO_LOGIN_PORT', 8082, 1, 65535),
    dbPath: text('DEMO_LOGIN_DB_PATH') ?? './data/demo-login.db',
    staticDir: text('DEMO_LOGIN_STATIC_DIR') ?? './dist/web',
    integrationApiUrl: text('EZKEY_INTEGRATION_API_URL') ?? 'http://localhost:7080',
    integrationKey: text('EZKEY_INTEGRATION_KEY'),
    secretKey: text('EZKEY_SECRET_KEY'),
    // Ezkey accepts 15 s minimum and caps the lifetime (typically at 120 s).
    authTtlSeconds: int('DEMO_LOGIN_AUTH_TTL_SECONDS', 120, 15, 300),
    sessionHours: int('DEMO_LOGIN_SESSION_HOURS', 8, 1, 720),
    cookieSecure: bool('DEMO_LOGIN_COOKIE_SECURE', false),
    trustProxy: bool('DEMO_LOGIN_TRUST_PROXY', false),
    loginRateLimitPerMinute: int('DEMO_LOGIN_RATE_LIMIT_PER_MINUTE', 10, 1, 10_000),
  };
}
