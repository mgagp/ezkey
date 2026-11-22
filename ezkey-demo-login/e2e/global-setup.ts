// Prepares the end-to-end run: image, Ezkey data, Demo Login users, Demo Device enrollments.
import { execFileSync } from 'node:child_process';
import { mkdirSync, rmSync } from 'node:fs';
import { resolve } from 'node:path';
import { chromium } from '@playwright/test';
import { adminApi, cli, enrollDevice, provision, REPO, SHOTS, startApp, CONTAINER, IMAGE } from './support';

export default async function globalSetup(): Promise<void> {
  mkdirSync(SHOTS, { recursive: true });

  // 1. Build the image from the working tree (the same image the clean start runs).
  execFileSync('docker', ['build', '-q', '-f', 'ezkey-demo-login/Dockerfile', '-t', IMAGE, '.'], { cwd: REPO, stdio: 'inherit' });

  // 2. Ezkey side: integrations, API key, enrollments (Admin API, Global Admin token).
  execFileSync('node', ['e2e/provision.mjs'], { stdio: 'inherit' });
  const p = provision();
  const e = p.enrollments;

  // 3. Demo Login side: a fresh database, then the application's own users.
  try {
    execFileSync('docker', ['rm', '-f', CONTAINER], { stdio: 'ignore' });
    execFileSync('docker', ['volume', 'rm', '-f', `${CONTAINER}-data`], { stdio: 'ignore' });
  } catch {
    /* nothing to clean */
  }
  rmSync(resolve(REPO, 'ezkey-demo-login/e2e/.state/app.log'), { force: true });
  await startApp();
  cli('user', 'add', 'alice', '--name', 'Alice Martin', '--ezkey-id', e.alice.userIdentifier);
  cli('user', 'add', 'carol', '--name', 'Carol Diaz', '--ezkey-id', e.carol.userIdentifier);
  cli('user', 'add', 'dave', '--name', 'Dave Roy', '--ezkey-id', e.dave.userIdentifier);
  cli('user', 'add', 'bob', '--name', 'Bob Tremblay', '--ezkey-id', e.bob.userIdentifier);
  cli('user', 'add', 'oscar', '--name', 'Oscar Other', '--ezkey-id', e.oscar.userIdentifier);
  cli('user', 'add', 'erin', '--name', 'Erin No-Device', '--ezkey-id', `erin-${p.run}`);
  cli('user', 'add', 'frank', '--name', 'Frank Disabled', '--ezkey-id', e.alice.userIdentifier);
  cli('user', 'disable', 'frank');

  // 4. Demo Device: activate alice, carol, dave and oscar; then deactivate carol and revoke dave.
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 430, height: 860 } });
  await enrollDevice(page, e.alice, '00-device-enroll-alice.png');
  await enrollDevice(page, e.carol);
  await enrollDevice(page, e.dave);
  await enrollDevice(page, e.oscar);
  await browser.close();

  const reason = encodeURIComponent('Demo Login end-to-end test: enrollment state case');
  await adminApi('POST', `/api/v1/enrollments/${e.carol.enrollmentId}/deactivate?reason=${reason}`);
  await adminApi('POST', `/api/v1/enrollments/${e.dave.enrollmentId}/revoke?reason=${reason}`);
}
