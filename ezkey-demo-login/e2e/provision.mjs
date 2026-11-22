// Provisions a clean-start stack for the Demo Login end-to-end tests, through the Admin API.
//
// Needs a Global Admin token: run once from the repository root
//   mvn -q test -pl ezkey-tests -Dtest=AdminTokenCreationTest
// (it caches ezkey-tests/.ezkey-test/admin-token.json), or set EZKEY_ADMIN_TOKEN.
//
// Creates two integrations (the Demo Login one and "another application"), an API key, and the
// enrollments the test matrix needs. Writes e2e/.state/provision.json. Binding to the Demo Device
// is done by the tests themselves (through its UI), from the proof tokens written here.

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '../..');
const ADMIN_API = process.env.EZKEY_ADMIN_API_URL ?? 'http://localhost:9080';

function adminToken() {
  if (process.env.EZKEY_ADMIN_TOKEN) return process.env.EZKEY_ADMIN_TOKEN;
  const file = resolve(repo, 'ezkey-tests/.ezkey-test/admin-token.json');
  return JSON.parse(readFileSync(file, 'utf8')).token;
}

const token = adminToken();

async function admin(method, path, body) {
  const res = await fetch(ADMIN_API + path, {
    method,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', Accept: 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status}: ${text.slice(0, 300)}`);
  return text ? JSON.parse(text) : {};
}

const run = Date.now().toString(36);

async function enrollment(integrationId, name, userIdentifier) {
  const created = await admin('POST', '/api/v1/enrollments', { integrationId, name, userIdentifier });
  const detail = await admin('GET', `/api/v1/enrollments/${created.enrollmentId}`);
  return {
    enrollmentId: created.enrollmentId,
    userIdentifier,
    challenge: created.enrollmentChallenge,
    proofToken: detail.enrollmentProofToken,
  };
}

const demo = await admin('POST', '/api/v1/integrations', {
  code: `demo-login-${run}`,
  name: 'Demo Login',
  description: 'Reference integration used by the Demo Login end-to-end tests',
});
const other = await admin('POST', '/api/v1/integrations', {
  code: `other-app-${run}`,
  name: 'Another application',
  description: 'A second integration, to show that enrollments do not cross applications',
});
const demoId = demo.integrationId ?? demo.id;
const otherId = other.integrationId ?? other.id;

const key = await admin('POST', '/api/v1/api-keys', { integrationId: demoId, description: 'Demo Login e2e' });

// User identifiers carry the run id so a rerun on the same stack never collides.
const u = (name) => `${name}-${run}`;
const state = {
  run,
  integrationId: demoId,
  otherIntegrationId: otherId,
  integrationKey: key.integrationKey,
  secretKey: key.secretKey,
  enrollments: {
    alice: await enrollment(demoId, 'Alice Martin', u('alice')), // bound, the happy path
    carol: await enrollment(demoId, 'Carol Diaz', u('carol')), // bound, then deactivated
    dave: await enrollment(demoId, 'Dave Roy', u('dave')), // bound, then revoked
    bob: await enrollment(demoId, 'Bob Tremblay', u('bob')), // created, never activated on a device
    oscar: await enrollment(otherId, 'Oscar Other', u('oscar')), // belongs to the other integration
  },
};

mkdirSync(resolve(here, '.state'), { recursive: true });
writeFileSync(resolve(here, '.state/provision.json'), JSON.stringify(state, null, 2), { mode: 0o600 });
console.log(`Provisioned run ${run}: integration ${demoId}, other ${otherId}, ${Object.keys(state.enrollments).length} enrollments.`);
