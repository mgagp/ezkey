/**
 * Validates direct runtime dependency licenses from package.json.
 *
 * Usage: node scripts/check-third-party-licenses.mjs
 */
import fs from 'fs';
import path from 'path';
import {fileURLToPath} from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, '..');

const ALLOWED_LICENSES = new Set([
  'Apache-2.0',
  'BSD',
  'BSD-2-Clause',
  'BSD-3-Clause',
  'ISC',
  'MIT',
  'MIT-0',
  'Python-2.0',
  'Unlicense',
  'Zlib',
]);

function readPackageMeta(depName) {
  const segments = depName.split('/');
  const pkgPath = path.join(root, 'node_modules', ...segments, 'package.json');
  return JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
}

function normalizeLicense(licenseValue) {
  if (licenseValue == null) {
    return 'Unknown';
  }
  if (typeof licenseValue === 'string') {
    return licenseValue.trim();
  }
  if (Array.isArray(licenseValue)) {
    return licenseValue
      .map(entry => normalizeLicense(entry?.type ?? entry))
      .filter(Boolean)
      .join(' OR ');
  }
  if (typeof licenseValue === 'object' && licenseValue.type) {
    return String(licenseValue.type).trim();
  }
  return 'Unknown';
}

function isAllowedLicense(licenseText) {
  if (licenseText === 'Unknown') {
    return false;
  }

  const alternatives = licenseText
    .split(/\s+OR\s+/i)
    .map(part => part.trim())
    .filter(Boolean);

  return alternatives.some(candidate => ALLOWED_LICENSES.has(candidate));
}

function main() {
  const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
  const dependencies = Object.keys(pkg.dependencies ?? {}).sort((left, right) =>
    left.localeCompare(right),
  );

  const results = [];
  const failures = [];

  for (const dependency of dependencies) {
    let version = '';
    let license = 'Unknown';

    try {
      const meta = readPackageMeta(dependency);
      version = meta.version ?? '';
      license = normalizeLicense(meta.license ?? meta.licenses);
    } catch {
      failures.push(`${dependency}: package metadata unavailable`);
      continue;
    }

    results.push({dependency, version, license});

    if (!isAllowedLicense(license)) {
      failures.push(`${dependency}@${version || '?'}: disallowed or unknown license \"${license}\"`);
    }
  }

  for (const result of results) {
    console.log(`${result.dependency}@${result.version}: ${result.license}`);
  }

  if (failures.length > 0) {
    console.error('\nLicense check failed:');
    for (const failure of failures) {
      console.error(`- ${failure}`);
    }
    process.exitCode = 1;
    return;
  }

  console.log(`\nValidated ${results.length} direct runtime dependency licenses.`);
}

main();