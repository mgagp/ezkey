// Provisions a clean-start stack for the Demo Login end-to-end tests, through the Admin API.
//
// Needs a Global Admin token in EZKEY_ADMIN_TOKEN (`npm run e2e` takes it from the
// ezkey-tests cache; see docs/e2e/README.md).
//
// Creates two integrations (the Demo Login one and "another application"), an API key, and the
// enrollments the test matrix needs, and returns them. Nothing is written to disk: the global setup
// hands the result to the tests through the environment. Binding to the Demo Device is done by the
// setup through its screens, from the proof tokens returned here.

const ADMIN_API = process.env.EZKEY_ADMIN_API_URL ?? 'http://localhost:9080';

export async function provisionStack() {
const token = process.env.EZKEY_ADMIN_TOKEN;
if (!token) throw new Error('EZKEY_ADMIN_TOKEN is not set (run `npm run e2e`, see docs/e2e/README.md)');

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

console.log(`Provisioned run ${run}: integration ${demoId}, other ${otherId}, ${Object.keys(state.enrollments).length} enrollments.`);
return state;
}
