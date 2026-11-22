// Demo Login — server entry point.

import { createServer } from 'node:http';
import { EzkeyIntegrationClient } from 'ezkey-javascript-sdk';
import { loadConfig } from './config.ts';
import { DemoLoginDb } from './db.ts';
import { createHandler } from './http.ts';
import { LoginFlow } from './login-flow.ts';
import { log } from './log.ts';

const config = loadConfig();
const db = new DemoLoginDb(config.dbPath);

const ezkey =
  config.integrationKey && config.secretKey
    ? new EzkeyIntegrationClient({
        baseUrl: config.integrationApiUrl,
        integrationKey: config.integrationKey,
        secretKey: config.secretKey,
      })
    : null;
if (!ezkey) {
  log.warn('ezkey_not_configured', {
    hint: 'set EZKEY_INTEGRATION_KEY and EZKEY_SECRET_KEY, then restart; sign-in is refused until then',
  });
}

const flow = new LoginFlow(db, ezkey, config.authTtlSeconds);
const server = createServer(createHandler(config, db, flow));
// The wait call long-polls Ezkey for up to 25 s; keep the socket open longer than that.
server.requestTimeout = 60_000;

setInterval(() => {
  const removed = db.deleteExpiredSessions();
  if (removed > 0) log.info('sessions_expired_removed', { count: removed });
}, 10 * 60_000).unref();

server.listen(config.port, () => {
  log.info('demo_login_started', {
    port: config.port,
    integrationApiUrl: config.integrationApiUrl,
    configured: ezkey != null,
    authTtlSeconds: config.authTtlSeconds,
    users: db.listUsers().length,
  });
});

function shutdown(signal: string): void {
  log.info('demo_login_stopping', { signal });
  server.close(() => {
    db.close();
    process.exit(0);
  });
  setTimeout(() => process.exit(0), 5000).unref();
}
process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
