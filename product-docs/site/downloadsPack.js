import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import AdmZip from 'adm-zip';
import { readMethodologyVersion } from './methodologyVersion.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SITE_DIR = __dirname;

export const GENERATED_DOWNLOADS_DIR = path.join(SITE_DIR, '.generated', 'downloads');
export const DOWNLOAD_PACK_FILENAME = 'ezkey-methodology-pack.zip';

const PACK_ROOT = 'ezkey-methodology-pack';
const DOCS_ROOT = path.resolve(SITE_DIR, '..');
const VERSION_FILE = path.join(DOCS_ROOT, 'methodology-version.properties');

export function prepareDownloadPack({ tree, resolveCorpusPath }) {
  rmrf(GENERATED_DOWNLOADS_DIR);
  mkdirp(GENERATED_DOWNLOADS_DIR);

  const zip = new AdmZip();
  const publicFiles = allFiles(tree);

  for (const urlPath of publicFiles) {
    const abs = resolveCorpusPath(urlPath);
    if (!abs || !fs.existsSync(abs)) continue;
    addLocalFile(zip, abs, toReferenceTarget(urlPath));
  }

  for (const urlPath of publicFiles) {
    if (!isWorkspaceStarterDoc(urlPath)) continue;
    const abs = resolveCorpusPath(urlPath);
    if (!abs || !fs.existsSync(abs)) continue;
    addLocalFile(zip, abs, toWorkspaceStarterTarget(urlPath));
  }

  if (fs.existsSync(VERSION_FILE)) {
    addLocalFile(zip, VERSION_FILE, 'reference/methodology-version.properties');
    addLocalFile(zip, VERSION_FILE, 'workspace-starter/product-docs/methodology-version.properties');
  }

  zip.addFile(
    `${PACK_ROOT}/START-HERE.md`,
    Buffer.from(buildStartHere(), 'utf8'),
  );
  zip.addFile(
    `${PACK_ROOT}/INSTALL.md`,
    Buffer.from(buildInstallGuide(), 'utf8'),
  );
  zip.addFile(
    `${PACK_ROOT}/pack-manifest.json`,
    Buffer.from(JSON.stringify(buildPackManifest(publicFiles), null, 2) + '\n', 'utf8'),
  );

  const archivePath = path.join(GENERATED_DOWNLOADS_DIR, DOWNLOAD_PACK_FILENAME);
  zip.writeZip(archivePath);

  return {
    archivePath,
    downloadDir: GENERATED_DOWNLOADS_DIR,
    publicFileCount: publicFiles.length,
  };
}

function addLocalFile(zip, abs, targetPath) {
  const normalized = targetPath.replace(/\\/g, '/');
  const dir = path.posix.dirname(normalized);
  const name = path.posix.basename(normalized);
  zip.addLocalFile(abs, `${PACK_ROOT}/${dir}`, name);
}

function toReferenceTarget(urlPath) {
  if (urlPath === 'glossary') return 'reference/glossary.md';
  return `reference/${urlPath}`;
}

function toWorkspaceStarterTarget(urlPath) {
  if (urlPath === 'glossary') return 'workspace-starter/product-docs/glossary.md';
  return `workspace-starter/product-docs/${urlPath}`;
}

function isWorkspaceStarterDoc(urlPath) {
  return urlPath === 'glossary' ||
    urlPath.startsWith('methodology/') ||
    urlPath.startsWith('templates/');
}

function allFiles(tree) {
  const out = [];
  const walk = (nodes) => {
    for (const node of nodes || []) {
      if (node.kind === 'file') out.push(node.path);
      if (node.children) walk(node.children);
    }
  };
  walk(tree);
  return out;
}

function buildPackManifest(publicFiles) {
  const version = readMethodologyVersion();
  return {
    id: 'ezkey-methodology-pack',
    generatedAt: new Date().toISOString(),
    methodologyVersion: version.version,
    methodologyReleased: version.released,
    archiveFile: DOWNLOAD_PACK_FILENAME,
    model: 'download-pack-first',
    usageModes: [
      'reference-pack',
      'workspace-starter',
    ],
    publicCorpusFiles: publicFiles,
    workspaceStarterIncludes: [
      'workspace-starter/product-docs/methodology/**',
      'workspace-starter/product-docs/templates/**',
      'workspace-starter/product-docs/glossary.md',
    ],
  };
}

function buildStartHere() {
  return `# Ezkey Methodology Download Pack

This archive is the smallest high-value local distribution of the Ezkey methodology.

It contains two usage paths:

1. \`reference/\` — read, review, and adapt the public methodology pack locally.
2. \`workspace-starter/\` — copy the methodology into a repo or workspace.

## Folder guide

- \`reference/methodology/\` — the core methodology document and release notes.
- \`reference/templates/\` — the four reusable templates.
- \`reference/glossary.md\` — glossary.
- \`workspace-starter/product-docs/\` — starter documentation structure to place in a repo.

Open \`INSTALL.md\` next.
`;
}

function buildInstallGuide() {
  return `# Install Guide

## Option 1 - Reference pack

Use this when you want a local copy for reading, review, or adaptation.

1. Unzip the archive.
2. Open the \`reference/\` folder.
3. Start with \`reference/methodology/README.md\` — the whole method fits in that one document.

## Option 2 - Workspace starter

Use this when you want to adopt the methodology inside an existing repo or a new workspace.

1. Copy \`workspace-starter/product-docs/\` into your target repository.
2. Keep the copied methodology docs as your local source of truth for process and templates.

## Notes

- This pack intentionally prefers packaging over installer automation.
- A richer installer should be added only if repeated manual adoption shows clear friction.
`;
}

function rmrf(targetPath) {
  if (fs.existsSync(targetPath)) {
    fs.rmSync(targetPath, { recursive: true, force: true });
  }
}

function mkdirp(targetPath) {
  fs.mkdirSync(targetPath, { recursive: true });
}