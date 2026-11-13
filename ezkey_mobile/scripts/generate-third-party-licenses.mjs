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
 *   - iOS native (#1148): ios/Podfile.lock. Pods from npm libraries are already covered by
 *     their JS entry and React Native's own pods by one entry, so only React Native, Hermes
 *     and the C/C++ libraries below are listed.
 *   - Both platforms: C/C++ libraries compiled into React Native's prebuilt binaries
 *     (RN_BUNDLED_NATIVE), with upstream texts committed under licenses/texts/.
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
];
const NATIVE_PLATFORMS = ['android', 'ios'];
const PODFILE_LOCK = path.join(root, 'ios', 'Podfile.lock');
const RN_VERSIONS_TOML = path.join(
  root,
  'node_modules',
  'react-native',
  'gradle',
  'libs.versions.toml',
);
const BUNDLED_TEXTS_DIR = path.join(root, 'licenses', 'texts');
const LICENSE_FILE = /^(licen[cs]e|copying)([-._].*)?$/i;
const SELF_PACKAGE = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8')).name;

/**
 * Android artifacts whose version the React Native Gradle plugin injects, which the
 * AboutLibraries export omits. Described from the npm package that pins their version.
 */
const RN_MANAGED_ANDROID_ARTIFACTS = [
  {
    uniqueId: 'com.facebook.react:react-android',
    name: 'React Native (Android)',
    npm: 'react-native',
  },
  {uniqueId: 'com.facebook.hermes:hermes-android', name: 'Hermes', npm: 'hermes-compiler'},
];

/**
 * iOS pods described from the npm package that ships them; the version is the pod's
 * version in ios/Podfile.lock (what the app was built with).
 */
const RN_MANAGED_IOS_PODS = [
  {pod: 'React-Core', name: 'React Native (iOS)', npm: 'react-native'},
  {pod: 'hermes-engine', name: 'Hermes', npm: 'hermes-compiler'},
];

/**
 * C/C++ libraries compiled into React Native's prebuilt native binaries: libreactnative.so
 * in react-android, ReactNativeDependencies.xcframework on iOS. Neither the AboutLibraries
 * export nor the CocoaPods acknowledgements list them, and the binaries ship no license
 * file, so the upstream texts (fetched at the pinned tag) are committed under licenses/texts/.
 * Versions are read from react-native itself (gradle/libs.versions.toml, ios/Podfile.lock),
 * so an RN bump updates them; re-check the texts when a major version changes.
 * `android` is the libs.versions.toml key, `ios` the pod name; absent = not on that platform.
 */
const RN_BUNDLED_NATIVE = [
  {
    name: 'Boost',
    license: 'BSL-1.0',
    text: 'boost-LICENSE_1_0.txt',
    website: 'https://www.boost.org',
    android: 'boost',
    ios: 'boost',
  },
  {
    name: 'double-conversion',
    license: 'BSD-3-Clause',
    text: 'double-conversion-LICENSE.txt',
    website: 'https://github.com/google/double-conversion',
    android: 'doubleconversion',
    ios: 'DoubleConversion',
  },
  {
    name: 'fast_float',
    license: 'Apache-2.0 OR BSL-1.0 OR MIT',
    text: 'fast_float-LICENSE-MIT.txt',
    website: 'https://github.com/fastfloat/fast_float',
    android: 'fastFloat',
    ios: 'fast_float',
  },
  {
    name: '{fmt}',
    license: 'MIT',
    text: 'fmt-LICENSE.txt',
    website: 'https://github.com/fmtlib/fmt',
    android: 'fmt',
    ios: 'fmt',
  },
  {
    name: 'Folly',
    license: 'Apache-2.0',
    text: 'folly-LICENSE.txt',
    website: 'https://github.com/facebook/folly',
    android: 'folly',
    ios: 'RCT-Folly',
  },
  {
    name: 'glog',
    license: 'BSD-3-Clause',
    text: 'glog-COPYING.txt',
    website: 'https://github.com/google/glog',
    android: 'glog',
    ios: 'glog',
  },
  {
    name: 'SocketRocket',
    license: 'BSD-3-Clause',
    text: 'SocketRocket-LICENSE.txt',
    website: 'https://github.com/facebookincubator/SocketRocket',
    ios: 'SocketRocket',
  },
];

/** Where the bundled libraries live, shown as the row's secondary label. */
const RN_BUNDLED_CONTAINER = {android: 'react-android', ios: 'ReactNativeDependencies'};

/** Copyright holder for MIT packages that ship no license file and no author field. */
const COPYRIGHT_HOLDER_OVERRIDES = {
  'metro-runtime': 'Meta Platforms, Inc. and affiliates.',
  'hermes-compiler': 'Meta Platforms, Inc. and affiliates.',
};

/** Vendor terms that are not open source licenses (shown in their own section). */
const PROPRIETARY_LICENSE_NAMES = [/terms of service/i, /software development kit license/i];

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
  // npm person string: "Name <email> (url)" — keep the name only.
  return raw ? raw.split(/[<(]/, 1)[0].trim() : '';
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
      COPYRIGHT_HOLDER_OVERRIDES[meta.name] ??
      (authorName(meta.author) || `the ${meta.name} authors`);
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

/** Top-level pod versions from ios/Podfile.lock (`  - Name (1.2.3):`), or throws. */
function podVersions() {
  const versions = new Map();
  for (const match of fs
    .readFileSync(PODFILE_LOCK, 'utf8')
    .matchAll(/^  - ([^\s/]+)(?:\/\S+)? \(([^)]+)\)/gm)) {
    if (!versions.has(match[1])) {
      versions.set(match[1], match[2]);
    }
  }
  return versions;
}

/** `name = "1_83_0"` keys from React Native's gradle/libs.versions.toml, dots for underscores. */
function androidRnVersions() {
  const versions = new Map();
  for (const match of fs
    .readFileSync(RN_VERSIONS_TOML, 'utf8')
    .matchAll(/^([A-Za-z][\w-]*)\s*=\s*"([^"]+)"/gm)) {
    versions.set(match[1], match[2].replace(/_/g, '.'));
  }
  return versions;
}

function requireVersion(versions, key, where) {
  const version = versions.get(key);
  if (!version) {
    throw new Error(
      `No version for ${key} in ${where}; update RN_BUNDLED_NATIVE or RN_MANAGED_IOS_PODS`,
    );
  }
  return version;
}

function collectRnManagedIos(pods, texts) {
  return RN_MANAGED_IOS_PODS.map(managed => {
    const dir = path.join(root, 'node_modules', ...managed.npm.split('/'));
    const meta = JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8'));
    const entry = npmEntry(dir, meta, texts, {
      id: `ios:${managed.pod}`,
      name: managed.name,
      source: 'ios',
      platforms: ['ios'],
    });
    return {...entry, version: requireVersion(pods, managed.pod, 'ios/Podfile.lock')};
  });
}

function collectRnBundled(platform, versions, texts) {
  const where = platform === 'ios' ? 'ios/Podfile.lock' : 'react-native/gradle/libs.versions.toml';
  return RN_BUNDLED_NATIVE.filter(library => library[platform]).map(library => {
    const normalized = normalizeText(
      fs.readFileSync(path.join(BUNDLED_TEXTS_DIR, library.text), 'utf8'),
    );
    // Shared by both platforms: one copy of each text in the snapshot.
    const id = textId('native', normalized);
    texts[id] = normalized;
    return {
      id: `${platform}:${RN_BUNDLED_CONTAINER[platform]}/${library[platform]}`,
      name: library.name,
      version: requireVersion(versions, library[platform], where),
      license: library.license,
      source: platform,
      platforms: [platform],
      website: library.website,
      textIds: [id],
    };
  });
}

function main() {
  const texts = {};
  const sourceOrder = ['js', ...NATIVE_PLATFORMS];
  const native = NATIVE_EXPORTS.flatMap(entry => collectNative(entry.platform, entry.file, texts));
  const pods = podVersions();
  const packages = [
    ...collectJs(texts),
    ...native,
    ...collectRnManagedAndroid(new Set(native.map(pkg => pkg.id)), texts),
    ...collectRnBundled('android', androidRnVersions(), texts),
    ...collectRnManagedIos(pods, texts),
    ...collectRnBundled('ios', pods, texts),
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
    note: 'Generated by scripts/generate-third-party-licenses.mjs (Metro release bundles, native exports, ios/Podfile.lock).',
    packages,
    texts: sortedTexts,
  };
  // Keep the previous timestamp when nothing else changed, so a no-op regeneration leaves the
  // file byte-identical (the Dependabot licenses workflow commits only on a real diff).
  const previousGeneratedAt = unchangedSnapshotTimestamp(out);
  if (previousGeneratedAt) {
    out.generatedAt = previousGeneratedAt;
  }
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

/**
 * Returns the existing snapshot's generatedAt when its content (all fields but generatedAt)
 * equals `next`, else null (missing, unreadable, or changed snapshot).
 */
function unchangedSnapshotTimestamp(next) {
  let previous;
  try {
    previous = JSON.parse(fs.readFileSync(outPath, 'utf8'));
  } catch {
    return null;
  }
  if (typeof previous?.generatedAt !== 'string') {
    return null;
  }
  const {generatedAt: _previousAt, ...previousRest} = previous;
  const {generatedAt: _nextAt, ...nextRest} = next;
  return JSON.stringify(previousRest) === JSON.stringify(nextRest) ? previous.generatedAt : null;
}

main();
