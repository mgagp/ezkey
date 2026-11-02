/**
 * Writes app/data/thirdPartyLicenses.json, the snapshot behind the in-app Open Source
 * Licenses screen (#1109). Full license texts are embedded (deduplicated) so the screen
 * works offline.
 *
 * Sources:
 *   - JS: packages Metro actually bundles in a release build, per platform (android, ios),
 *     read from release-mode bundle sourcemaps. Build-time tooling never reaches the app.
 *   - Android native: licenses/android-libraries.json (AboutLibraries export, release
 *     runtime classpath). Refresh with `yarn license:android`.
 *   - iOS native: licenses/ios-libraries.json when present, same shape as the Android
 *     export (#1148).
 *
 * Usage: node scripts/generate-third-party-licenses.mjs
 */
import {spawnSync} from 'child_process';
import crypto from 'crypto';
import fs from 'fs';
import os from 'os';
import path from 'path';
import {fileURLToPath} from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, '..');
const outPath = path.join(root, 'app', 'data', 'thirdPartyLicenses.json');

const JS_PLATFORMS = ['android', 'ios'];
const NATIVE_EXPORTS = [
  {platform: 'android', file: path.join(root, 'licenses', 'android-libraries.json')},
  {platform: 'ios', file: path.join(root, 'licenses', 'ios-libraries.json')},
];
const LICENSE_FILE = /^(licen[cs]e|copying)([-._].*)?$/i;
const SELF_PACKAGE = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8')).name;

/**
 * Android artifacts whose version the React Native Gradle plugin injects, which the
 * AboutLibraries export omits. Described from the npm package that pins their version.
 */
const RN_MANAGED_ANDROID_ARTIFACTS = [
  {uniqueId: 'com.facebook.react:react-android', name: 'React Native (Android)', npm: 'react-native'},
  {uniqueId: 'com.facebook.hermes:hermes-android', name: 'Hermes', npm: 'hermes-compiler'},
];

/** Copyright holder for MIT packages that ship no license file and no author field. */
const COPYRIGHT_HOLDER_OVERRIDES = {
  'metro-runtime': 'Meta Platforms, Inc. and affiliates.',
  'hermes-compiler': 'Meta Platforms, Inc. and affiliates.',
};

/** Vendor terms that are not open source licenses (shown in their own section). */
const PROPRIETARY_LICENSE_NAMES = [
  /terms of service/i,
  /software development kit license/i,
];

function normalizeLicense(value) {
  if (value == null) {
    return 'Unknown';
  }
  if (typeof value === 'string') {
    return value.trim();
  }
  if (Array.isArray(value)) {
    return value.map(entry => normalizeLicense(entry?.type ?? entry)).join(' OR ');
  }
  if (typeof value === 'object' && value.type) {
    return String(value.type).trim();
  }
  return 'Unknown';
}

function normalizeRepoUrl(repository) {
  const raw = typeof repository === 'string' ? repository : repository?.url;
  if (!raw) {
    return '';
  }
  return raw
    .trim()
    .replace(/^git\+/, '')
    .replace(/^git:\/\//, 'https://')
    .replace(/^git@github\.com:/, 'https://github.com/')
    .replace(/^github:/, 'https://github.com/')
    .replace(/\.git$/, '')
    .replace(/\/$/, '')
    .toLowerCase();
}

function websiteOf(meta) {
  if (typeof meta.homepage === 'string' && meta.homepage.startsWith('http')) {
    return meta.homepage;
  }
  const repo = normalizeRepoUrl(meta.repository);
  return repo.startsWith('http') ? repo : undefined;
}

function authorName(author) {
  const raw = typeof author === 'string' ? author : author?.name;
  return raw ? raw.replace(/<[^>]*>|\([^)]*\)/g, '').trim() : '';
}

function readLicenseFile(dir) {
  let entries;
  try {
    entries = fs.readdirSync(dir);
  } catch {
    return null;
  }
  const match = entries.filter(name => LICENSE_FILE.test(name)).sort()[0];
  return match ? fs.readFileSync(path.join(dir, match), 'utf8') : null;
}

function mitTemplate(holder) {
  return `MIT License

Copyright (c) ${holder}

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
`;
}

/** SPDX template markup (`<<var;…;original=…;match=…>>`, optional markers) to plain text. */
function cleanSpdxTemplate(text) {
  return text
    .replace(/<<var;[^>]*?original=([^;>]*)[^>]*>>/g, '$1')
    .replace(/<<(beginOptional|endOptional)[^>]*>>/g, '')
    .replace(/\r\n/g, '\n');
}

function normalizeText(text) {
  return `${text.replace(/\r\n/g, '\n').trim()}\n`;
}

function textId(prefix, text) {
  return `${prefix}:${crypto.createHash('sha256').update(text).digest('hex').slice(0, 12)}`;
}

function bundleSources(platform, tempDir) {
  const bundle = path.join(tempDir, `${platform}.js`);
  const map = path.join(tempDir, `${platform}.map`);
  const result = spawnSync(
    process.execPath,
    [
      path.join(root, 'node_modules', 'react-native', 'cli.js'),
      'bundle',
      '--platform',
      platform,
      '--dev',
      'false',
      '--minify',
      'false',
      '--entry-file',
      'index.js',
      '--bundle-output',
      bundle,
      '--sourcemap-output',
      map,
    ],
    {cwd: root, encoding: 'utf8'},
  );
  if (result.status !== 0) {
    throw new Error(`Metro bundle failed for ${platform}:\n${result.stdout}\n${result.stderr}`);
  }
  const doc = JSON.parse(fs.readFileSync(map, 'utf8'));
  // Metro CLI emits an indexed (sectioned) map; the Gradle bundle task a flat one.
  return doc.sections ? doc.sections.flatMap(section => section.map?.sources ?? []) : doc.sources;
}

/** Package directories (node_modules/<name>, nested allowed) that Metro put in the bundle. */
function packageDirsFromSources(sources) {
  const dirs = new Set();
  for (const source of sources) {
    const normalized = source.split('\\').join('/');
    const index = normalized.lastIndexOf('node_modules/');
    if (index < 0) {
      continue;
    }
    const rest = normalized.slice(index + 'node_modules/'.length).split('/');
    const name = rest[0].startsWith('@') ? `${rest[0]}/${rest[1]}` : rest[0];
    dirs.add(path.normalize(normalized.slice(0, index + 'node_modules/'.length) + name));
  }
  return dirs;
}

/** Repo URL → license text, from every installed package that ships a license file. */
function indexLicensesByRepo() {
  const byRepo = new Map();
  const modules = path.join(root, 'node_modules');
  const visit = dir => {
    let meta;
    try {
      meta = JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8'));
    } catch {
      return;
    }
    const repo = normalizeRepoUrl(meta.repository);
    const text = repo && !byRepo.has(repo) ? readLicenseFile(dir) : null;
    if (text) {
      byRepo.set(repo, text);
    }
  };
  for (const entry of fs.readdirSync(modules)) {
    if (entry.startsWith('.')) {
      continue;
    }
    if (entry.startsWith('@')) {
      for (const scoped of fs.readdirSync(path.join(modules, entry))) {
        visit(path.join(modules, entry, scoped));
      }
    } else {
      visit(path.join(modules, entry));
    }
  }
  return byRepo;
}

function collectJs(texts) {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'ezkey-licenses-'));
  const platformsByDir = new Map();
  try {
    for (const platform of JS_PLATFORMS) {
      for (const dir of packageDirsFromSources(bundleSources(platform, tempDir))) {
        if (!platformsByDir.has(dir)) {
          platformsByDir.set(dir, new Set());
        }
        platformsByDir.get(dir).add(platform);
      }
    }
  } finally {
    fs.rmSync(tempDir, {recursive: true, force: true});
  }

  const packages = [];
  for (const [dir, platforms] of platformsByDir) {
    const meta = JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8'));
    if (meta.name === SELF_PACKAGE) {
      continue;
    }
    packages.push(
      npmEntry(dir, meta, texts, {
        id: `js:${meta.name}@${meta.version}`,
        name: meta.name,
        source: 'js',
        platforms: JS_PLATFORMS.filter(platform => platforms.has(platform)),
      }),
    );
  }
  return packages;
}

let licensesByRepoCache = null;

/** Snapshot entry for an npm package: license id, website, and resolved full text. */
function npmEntry(dir, meta, texts, base) {
  licensesByRepoCache ??= indexLicensesByRepo();
  const license = normalizeLicense(meta.license ?? meta.licenses);
  let text = readLicenseFile(dir) ?? licensesByRepoCache.get(normalizeRepoUrl(meta.repository));
  if (!text && license === 'MIT') {
    const holder =
      COPYRIGHT_HOLDER_OVERRIDES[meta.name] ?? (authorName(meta.author) || `the ${meta.name} authors`);
    text = mitTemplate(holder);
  }
  const entry = {...base, version: meta.version ?? '', license};
  const website = websiteOf(meta);
  if (website) {
    entry.website = website;
  }
  if (text) {
    const normalized = normalizeText(text);
    const id = textId(base.source, normalized);
    texts[id] = normalized;
    entry.textIds = [id];
  }
  return entry;
}

function collectRnManagedAndroid(existingIds, texts) {
  return RN_MANAGED_ANDROID_ARTIFACTS.filter(
    artifact => !existingIds.has(`android:${artifact.uniqueId}`),
  ).map(artifact => {
    const dir = path.join(root, 'node_modules', ...artifact.npm.split('/'));
    const meta = JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8'));
    return npmEntry(dir, meta, texts, {
      id: `android:${artifact.uniqueId}`,
      name: artifact.name,
      source: 'android',
      platforms: ['android'],
    });
  });
}

function collectNative(platform, file, texts) {
  if (!fs.existsSync(file)) {
    return [];
  }
  const doc = JSON.parse(fs.readFileSync(file, 'utf8'));
  const licenses = doc.licenses ?? {};
  const packages = [];
  for (const library of doc.libraries ?? []) {
    const ids = library.licenses ?? [];
    const defs = ids.map(id => ({id, ...(licenses[id] ?? {})}));
    const entry = {
      id: `${platform}:${library.uniqueId}`,
      name: library.name || library.uniqueId,
      version: library.artifactVersion ?? '',
      license: defs.map(def => def.spdxId || def.name || def.id).join(', ') || 'Unknown',
      source: platform,
      platforms: [platform],
    };
    if (defs.some(def => PROPRIETARY_LICENSE_NAMES.some(pattern => pattern.test(def.name ?? '')))) {
      entry.proprietary = true;
    }
    if (library.website) {
      entry.website = library.website;
    }
    const textIds = [];
    const links = [];
    for (const def of defs) {
      if (def.content) {
        const normalized = normalizeText(cleanSpdxTemplate(def.content));
        const id = textId(platform, normalized);
        texts[id] = normalized;
        textIds.push(id);
      } else if (def.url) {
        links.push(def.url);
      }
    }
    if (textIds.length > 0) {
      entry.textIds = textIds;
    }
    if (links.length > 0) {
      entry.termsUrls = links;
    }
    packages.push(entry);
  }
  return packages;
}

function main() {
  const texts = {};
  const sourceOrder = ['js', ...NATIVE_EXPORTS.map(native => native.platform)];
  const native = NATIVE_EXPORTS.flatMap(entry => collectNative(entry.platform, entry.file, texts));
  const packages = [
    ...collectJs(texts),
    ...native,
    ...collectRnManagedAndroid(new Set(native.map(pkg => pkg.id)), texts),
  ].sort(
    (a, b) =>
      sourceOrder.indexOf(a.source) - sourceOrder.indexOf(b.source) ||
      a.name.localeCompare(b.name, 'en') ||
      a.id.localeCompare(b.id, 'en'),
  );
  const sortedTexts = Object.fromEntries(
    Object.keys(texts)
      .sort()
      .map(id => [id, texts[id]]),
  );

  const out = {
    generatedAt: new Date().toISOString(),
    note: 'Generated by scripts/generate-third-party-licenses.mjs (Metro release bundles + native exports).',
    packages,
    texts: sortedTexts,
  };
  fs.mkdirSync(path.dirname(outPath), {recursive: true});
  fs.writeFileSync(outPath, `${JSON.stringify(out, null, 2)}\n`, 'utf8');
  const missing = packages.filter(pkg => !pkg.textIds && !pkg.termsUrls).map(pkg => pkg.id);
  console.log(
    `Wrote ${packages.length} packages and ${Object.keys(sortedTexts).length} license texts to ${path.relative(root, outPath)}`,
  );
  if (missing.length > 0) {
    console.warn(`No license text or terms link for: ${missing.join(', ')}`);
  }
}

main();
