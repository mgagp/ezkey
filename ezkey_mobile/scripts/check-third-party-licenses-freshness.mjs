/**
 * Fails when app/data/thirdPartyLicenses.json is stale relative to package.json
 * dependencies (and installed package metadata).
 *
 * Regenerates via generate-third-party-licenses.mjs into a temp file, then compares
 * the committed snapshot to the regenerated one while ignoring generatedAt so
 * timestamp-only drift does not fail CI.
 *
 * Usage: node scripts/check-third-party-licenses-freshness.mjs
 *
 * When this fails (including on Dependabot PRs): from ezkey_mobile/ run
 *   yarn license:app-data
 * and commit app/data/thirdPartyLicenses.json on the same branch.
 */
import {spawnSync} from 'child_process';
import fs from 'fs';
import os from 'os';
import path from 'path';
import {fileURLToPath} from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, '..');
const committedPath = path.join(root, 'app', 'data', 'thirdPartyLicenses.json');
const generateScript = path.join(__dirname, 'generate-third-party-licenses.mjs');

function stripGeneratedAt(doc) {
  if (doc == null || typeof doc !== 'object') {
    return doc;
  }
  const {generatedAt: _generatedAt, ...rest} = doc;
  return rest;
}

function stableStringify(value) {
  return `${JSON.stringify(value, null, 2)}\n`;
}

function main() {
  if (!fs.existsSync(committedPath)) {
    console.error(`Missing committed license snapshot: ${path.relative(root, committedPath)}`);
    console.error('Run: yarn license:app-data');
    process.exitCode = 1;
    return;
  }

  let committed;
  try {
    committed = JSON.parse(fs.readFileSync(committedPath, 'utf8'));
  } catch (error) {
    console.error(`Failed to parse committed license snapshot: ${error.message}`);
    process.exitCode = 1;
    return;
  }

  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'ezkey-license-freshness-'));
  const tempOut = path.join(tempDir, 'thirdPartyLicenses.json');
  const backup = fs.readFileSync(committedPath);

  try {
    const generate = spawnSync(process.execPath, [generateScript], {
      cwd: root,
      encoding: 'utf8',
    });
    if (generate.status !== 0) {
      console.error(generate.stdout || '');
      console.error(generate.stderr || '');
      console.error('License snapshot regeneration failed.');
      process.exitCode = 1;
      return;
    }

    fs.copyFileSync(committedPath, tempOut);
  } finally {
    fs.writeFileSync(committedPath, backup);
  }

  let regenerated;
  try {
    regenerated = JSON.parse(fs.readFileSync(tempOut, 'utf8'));
  } catch (error) {
    console.error(`Failed to parse regenerated license snapshot: ${error.message}`);
    process.exitCode = 1;
    return;
  } finally {
    fs.rmSync(tempDir, {recursive: true, force: true});
  }

  const committedNorm = stableStringify(stripGeneratedAt(committed));
  const regeneratedNorm = stableStringify(stripGeneratedAt(regenerated));

  if (committedNorm === regeneratedNorm) {
    const packageCount = Array.isArray(regenerated.packages) ? regenerated.packages.length : 0;
    console.log(
      `License snapshot is fresh (${packageCount} packages; generatedAt ignored for comparison).`,
    );
    return;
  }

  console.error('Committed third-party license snapshot is stale.');
  console.error(`File: ${path.relative(root, committedPath)}`);
  console.error('');
  console.error('CI compares the committed JSON to a fresh yarn license:app-data run and');
  console.error('ignores only the generatedAt field. Package list / versions / licenses must match.');
  console.error('');
  console.error('Fix (from ezkey_mobile/, Linux or Windows Git Bash):');
  console.error('  yarn license:app-data');
  console.error('  git add app/data/thirdPartyLicenses.json && git commit');
  console.error('');
  console.error('Dependabot / dependency PRs: regenerate and commit the snapshot on the same');
  console.error('branch after package.json / yarn.lock change. No bot write token is required.');
  process.exitCode = 1;
}

main();
