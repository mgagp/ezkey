/**
 * One-shot converter: Postman Collection v2.1 JSON → single Bruno collection at bruno/.
 * Uses @usebruno/converters + @usebruno/filestore (Bruno CLI 4 no longer imports Postman).
 *
 * Requires a Postman export tree at postman/collections/v2.1 and postman/environments
 * (removed from the repo after cutover; restore from git history only if re-running).
 *
 * Usage (from repo root, after cd bruno && npm install):
 *   node scripts/bruno-import-from-postman.js
 */
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const collectionsDir = path.join(root, "postman", "collections", "v2.1");
const envDir = path.join(root, "postman", "environments");
const brunoDir = path.join(root, "bruno");

const { postmanToBruno, postmanToBrunoEnvironment } = require(path.join(
  brunoDir,
  "node_modules",
  "@usebruno",
  "converters",
));
const { stringifyRequest, stringifyFolder, stringifyEnvironment } = require(
  path.join(brunoDir, "node_modules", "@usebruno", "filestore"),
);

/** Classic Bru (CLI `bru run`); filestore default is OpenCollection YAML. */
const BRU_FORMAT = { format: "bru" };

const ENV_NAME_MAP = {
  local: "local",
  "local (via Caddy proxy)": "local-via-caddy-proxy",
  "local with ngrok": "local-ngrok",
};

const SKIP_ENV_PREFIXES = ["env_simulation"];

function slugifyCollection(fileName) {
  return fileName
    .replace(/\.postman_collection\.json$/i, "")
    .replace(/^EZ Key\s+/i, "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function safeFileName(name) {
  return String(name)
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 120) || "request";
}

function rmrf(dir) {
  fs.rmSync(dir, { recursive: true, force: true });
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

async function writeItems(items, destDir) {
  ensureDir(destDir);
  let count = 0;
  const used = new Map();
  for (const item of items || []) {
    if (item.type === "folder" || item.items) {
      const folderName = safeFileName(item.name);
      const folderDir = path.join(destDir, folderName);
      ensureDir(folderDir);
      try {
        const folderBru = await stringifyFolder(
          {
            name: item.name,
            uid: item.uid,
            seq: item.seq,
            request: item.request,
            root: item.root,
          },
          BRU_FORMAT,
        );
        if (folderBru) {
          fs.writeFileSync(path.join(folderDir, "folder.bru"), folderBru, "utf8");
        }
      } catch (_) {
        // folder meta optional
      }
      count += await writeItems(item.items || [], folderDir);
      continue;
    }

    let base = safeFileName(item.name);
    const n = (used.get(base) || 0) + 1;
    used.set(base, n);
    if (n > 1) {
      base = `${base}-${n}`;
    }
    const bru = await stringifyRequest(item, BRU_FORMAT);
    fs.writeFileSync(path.join(destDir, `${base}.bru`), bru, "utf8");
    count += 1;
  }
  return count;
}

function clearImportedFolders() {
  const keep = new Set([
    "package.json",
    "package-lock.json",
    "bruno.json",
    "collection.bru",
    "README.md",
    "environments",
    "node_modules",
    ".gitignore",
  ]);
  for (const entry of fs.readdirSync(brunoDir)) {
    if (keep.has(entry)) {
      continue;
    }
    rmrf(path.join(brunoDir, entry));
  }
}

function scrubEnvSecrets(envObj) {
  const vars = envObj.variables || envObj.vars || envObj.values || [];
  const list = Array.isArray(vars) ? vars : Object.entries(vars).map(([name, value]) => ({ name, value }));
  for (const v of list) {
    const key = v.name || v.key;
    if (!key) {
      continue;
    }
    if (SKIP_ENV_PREFIXES.some((p) => key.startsWith(p))) {
      v.value = "";
      if (v.enabled !== undefined) {
        v.enabled = false;
      }
      continue;
    }
    if (
      (/token|secret|privatekey|password|recovery/i.test(key) ||
        /privateKey|secretKey|Token$/.test(key)) &&
      !/base_url|username|requested|expires|status|type|Id$|id$/i.test(key)
    ) {
      v.value = "";
    }
  }
  return envObj;
}

async function convertEnvironments() {
  const outDir = path.join(brunoDir, "environments");
  ensureDir(outDir);
  for (const file of fs.readdirSync(envDir).filter((f) => f.endsWith(".postman_environment.json"))) {
    const raw = JSON.parse(fs.readFileSync(path.join(envDir, file), "utf8"));
    let env = await postmanToBrunoEnvironment(raw);
    env = scrubEnvSecrets(env);
    if (ENV_NAME_MAP[raw.name]) {
      env.name = ENV_NAME_MAP[raw.name];
    }
    const bru = await stringifyEnvironment(env, BRU_FORMAT);
    const outName = `${env.name || ENV_NAME_MAP[raw.name] || "env"}.bru`;
    fs.writeFileSync(path.join(outDir, outName), bru, "utf8");
    console.log("env", outName);
  }
}

async function main() {
  clearImportedFolders();
  const files = fs
    .readdirSync(collectionsDir)
    .filter((f) => f.endsWith(".postman_collection.json"))
    .sort();

  console.log("Converting", files.length, "collections…");
  let total = 0;
  for (const file of files) {
    const slug = slugifyCollection(file);
    const source = path.join(collectionsDir, file);
    const postman = JSON.parse(fs.readFileSync(source, "utf8"));
    const { collection, issues } = await postmanToBruno(postman);
    if (issues && issues.length) {
      console.warn("  issues for", slug, issues.slice(0, 5));
    }
    const dest = path.join(brunoDir, slug);
    const count = await writeItems(collection.items || [], dest);
    total += count;
    console.log("→", slug, count, "requests");
  }

  await convertEnvironments();
  console.log("Done.", total, "requests total");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
