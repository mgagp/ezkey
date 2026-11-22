// Helpers for the Demo Login end-to-end tests (clean-start stack + a dedicated Demo Login container).
import { execFileSync, spawnSync } from 'node:child_process';
import { appendFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import type { Page } from '@playwright/test';

const here = dirname(fileURLToPath(import.meta.url));
export const REPO = resolve(here, '../..');
export const SHOTS = resolve(here, '../docs/e2e/screenshots');

export const APP_URL = process.env.E2E_APP_URL ?? 'http://localhost:18082';
export const DEVICE_URL = process.env.E2E_DEVICE_URL ?? 'http://localhost:8083';
export const ADMIN_API = process.env.EZKEY_ADMIN_API_URL ?? 'http://localhost:9080';
export const IMAGE = process.env.E2E_IMAGE ?? 'ezkey-demo-login:local';
export const NETWORK = process.env.E2E_NETWORK ?? 'ezkey_ezkey-network';
export const CONTAINER = 'ezkey-demo-login-e2e';
/** Short lifetime so the "expired" case runs in seconds; the approval steps are automated. */
export const TTL_SECONDS = 30;

export interface Enrollment {
  enrollmentId: number;
  userIdentifier: string;
  challenge: number;
  proofToken: string;
}

export interface Provision {
  run: string;
  integrationKey: string;
  secretKey: string;
  enrollments: Record<'alice' | 'carol' | 'dave' | 'bob' | 'oscar', Enrollment>;
}

/** Provisioning result, handed over by the global setup through the environment (never on disk). */
export function provision(): Provision {
  const raw = process.env.E2E_PROVISION;
  if (!raw) throw new Error('E2E_PROVISION is not set: run the tests through Playwright (global setup)');
  return JSON.parse(raw) as Provision;
}

function docker(...args: string[]): string {
  return execFileSync('docker', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
}

export interface AppEnv {
  integrationApiUrl?: string;
  integrationKey?: string | null;
  secretKey?: string | null;
  rateLimitPerMinute?: number;
}

/** (Re)starts the Demo Login container under test with the given settings; the database volume persists. */
export async function startApp(env: AppEnv = {}): Promise<void> {
  const p = provision();
  try {
    // Keep each run's logs: the container is replaced on every configuration change.
    mkdirSync(resolve(here, '.state'), { recursive: true });
    appendFileSync(resolve(here, '.state/app.log'), appLogs());
  } catch {
    /* first start */
  }
  try {
    docker('rm', '-f', CONTAINER);
  } catch {
    /* not running */
  }
  const vars: Record<string, string> = {
    EZKEY_INTEGRATION_API_URL: env.integrationApiUrl ?? 'http://integration-api:7080',
    DEMO_LOGIN_AUTH_TTL_SECONDS: String(TTL_SECONDS),
    DEMO_LOGIN_RATE_LIMIT_PER_MINUTE: String(env.rateLimitPerMinute ?? 1000),
  };
  const key = env.integrationKey === undefined ? p.integrationKey : env.integrationKey;
  const secret = env.secretKey === undefined ? p.secretKey : env.secretKey;
  if (key) vars.EZKEY_INTEGRATION_KEY = key;
  if (secret) vars.EZKEY_SECRET_KEY = secret;
  docker(
    'run', '-d', '--name', CONTAINER, '--network', NETWORK, '-p', '18082:8082',
    '-v', `${CONTAINER}-data:/app/data`,
    ...Object.entries(vars).flatMap(([k, v]) => ['-e', `${k}=${v}`]),
    IMAGE,
  );
  for (let i = 0; i < 50; i++) {
    try {
      if ((await fetch(`${APP_URL}/healthz`)).ok) return;
    } catch {
      /* starting */
    }
    await new Promise((r) => setTimeout(r, 200));
  }
  throw new Error('Demo Login container did not become healthy');
}

export function cli(...args: string[]): string {
  return docker('exec', CONTAINER, 'node', 'server/cli.js', ...args);
}

/** Container logs (stdout and stderr together). */
export function appLogs(): string {
  const r = spawnSync('docker', ['logs', CONTAINER], { encoding: 'utf8' });
  return `${r.stdout}${r.stderr}`;
}

export async function adminApi(method: string, path: string): Promise<unknown> {
  const token = process.env.EZKEY_ADMIN_TOKEN;
  if (!token) throw new Error('EZKEY_ADMIN_TOKEN is not set');
  const res = await fetch(ADMIN_API + path, { method, headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' } });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status}: ${text.slice(0, 200)}`);
  return text ? JSON.parse(text) : {};
}

/** Enrolls the Demo Device with an enrollment (bind, then verify with the enrollment challenge). */
export async function enrollDevice(page: Page, e: Enrollment, shot?: string): Promise<void> {
  await page.goto(`${DEVICE_URL}/phone/ezkey/enrollment/new`);
  await page.getByTestId('demo-device-enrollment-token-input').fill(e.proofToken);
  await page.getByTestId('demo-device-start-enrollment').click();
  await page.getByTestId('demo-device-enroll-challenge-input').fill(String(e.challenge));
  if (shot) await page.screenshot({ path: `${SHOTS}/${shot.replace(/\.png$/, '')}.png` });
  await page.getByTestId('demo-device-verify-enrollment-submit').click();
  await page.waitForURL(/\/phone\/ezkey/);
}

/** Opens the pending request on the Demo Device (the device "pulls" it, like the mobile app). */
export async function openOnDevice(page: Page, enrollmentId: number): Promise<void> {
  for (let i = 0; i < 20; i++) {
    await page.goto(`${DEVICE_URL}/phone/ezkey/enrollments/${enrollmentId}/auth`);
    if (await page.getByTestId('demo-device-auth-form').isVisible()) return;
    await page.waitForTimeout(500);
  }
  throw new Error(`No pending request on the Demo Device for enrollment ${enrollmentId}`);
}

export async function answerOnDevice(page: Page, approve: boolean, challenge?: string, shot?: string): Promise<void> {
  const input = page.getByTestId('demo-device-challenge-input');
  // The Demo Device form marks the code as required even to deny, so a deny fills it too.
  if (await input.isVisible()) await input.fill(challenge ?? '00');
  if (shot) await page.screenshot({ path: `${SHOTS}/${shot.replace(/\.png$/, '')}.png` });
  await page.getByTestId(approve ? 'demo-device-approve-button' : 'demo-device-deny-button').click();
  await page.getByTestId('demo-device-result-card').waitFor();
}
