#!/usr/bin/env node
/**
 * Age-aware cleanup for Cloudflare Pages deployments (preview or production).
 *
 * Uses the Cloudflare REST API (created_on timestamps) — not Wrangler's relative
 * "Status" field — so age filters like 24h / 7d are reliable.
 *
 * Default (no flags): dry-run, preview, both ezkey-org + methodology-ezkey-org,
 * delete candidates older than 24h (aggressive profile).
 *
 * Requires: CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID (repo root .env or env).
 */

import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, '../..');

const ALL_PROJECTS = ['ezkey-org', 'methodology-ezkey-org', 'ezkey-admin-ui'];
const DEFAULT_PROJECTS = ['ezkey-org', 'methodology-ezkey-org'];

const MS_HOUR = 60 * 60 * 1000;
const MS_DAY = 24 * MS_HOUR;

function loadDotEnv() {
  try {
    const text = readFileSync(resolve(REPO_ROOT, '.env'), 'utf8');
    for (const line of text.split('\n')) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const m = trimmed.match(/^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/);
      if (m && process.env[m[1]] === undefined) {
        process.env[m[1]] = m[2];
      }
    }
  } catch {
    /* optional */
  }
}

function parseDuration(raw) {
  const m = String(raw).trim().match(/^(\d+(?:\.\d+)?)(h|d)$/i);
  if (!m) {
    throw new Error(`invalid duration "${raw}" (use e.g. 24h or 7d)`);
  }
  const n = Number(m[1]);
  const unit = m[2].toLowerCase();
  return unit === 'h' ? n * MS_HOUR : n * MS_DAY;
}

function parseArgs(argv) {
  const opts = {
    apply: false,
    env: 'preview',
    projects: [...DEFAULT_PROJECTS],
    olderThanMs: 24 * MS_HOUR,
    keepLatest: null,
    profile: 'aggressive',
    help: false,
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
      case '--help':
      case '-h':
        opts.help = true;
        break;
      case '--apply':
        opts.apply = true;
        break;
      case '--env':
        opts.env = argv[++i];
        if (opts.env !== 'preview' && opts.env !== 'production') {
          throw new Error('--env must be preview or production');
        }
        break;
      case '--project': {
        const p = argv[++i];
        if (p === 'all') {
          opts.projects = [...ALL_PROJECTS];
        } else if (!ALL_PROJECTS.includes(p)) {
          throw new Error(`unknown project "${p}" (known: ${ALL_PROJECTS.join(', ')}, all)`);
        } else {
          opts.projects = [p];
        }
        break;
      }
      case '--older-than':
        opts.olderThanMs = parseDuration(argv[++i]);
        opts.profile = 'custom';
        break;
      case '--profile': {
        const profile = argv[++i];
        if (profile !== 'aggressive' && profile !== 'prudent') {
          throw new Error('--profile must be aggressive (24h) or prudent (7d)');
        }
        opts.profile = profile;
        opts.olderThanMs = profile === 'prudent' ? 7 * MS_DAY : 24 * MS_HOUR;
        break;
      }
      case '--keep-latest': {
        const n = Number.parseInt(argv[++i], 10);
        if (!Number.isFinite(n) || n < 1) {
          throw new Error('--keep-latest must be a positive integer');
        }
        opts.keepLatest = n;
        break;
      }
      default:
        throw new Error(`unknown argument: ${arg}`);
    }
  }

  if (opts.keepLatest === null) {
    opts.keepLatest = opts.env === 'production' ? 3 : 1;
  }

  return opts;
}

function printHelp() {
  console.log(`Usage: cleanup-pages-deployments.sh [options]

Age-aware cleanup for Cloudflare Pages deployments. Dry-run by default.

Options:
  --project NAME|all     Pages project (default: ezkey-org + methodology-ezkey-org)
                         Known: ${ALL_PROJECTS.join(', ')}, all
  --env preview|production
                         Target environment (default: preview)
  --profile aggressive|prudent
                         aggressive = older than 24h (default)
                         prudent    = older than 7d
  --older-than DURATION  Override profile (e.g. 24h, 48h, 7d)
  --keep-latest N        Keep N newest deployment(s) per branch (default: 1 preview, 3 production)
  --apply                Actually delete (default: list only)
  -h, --help             Show this help

Examples:
  ./scripts/cloudflare/cleanup-pages-deployments.sh
  ./scripts/cloudflare/cleanup-pages-deployments.sh --profile prudent --apply
  ./scripts/cloudflare/cleanup-pages-deployments.sh --env production --profile prudent --apply
  ./scripts/cloudflare/cleanup-pages-deployments.sh --project ezkey-org --apply

Safety (production):
  - Never deletes deployments with a custom-domain alias (e.g. ezkey.org).
  - Keeps --keep-latest newest successful deployments per branch.
  - Requires explicit --env production (never the default).

Requires CLOUDFLARE_API_TOKEN + CLOUDFLARE_ACCOUNT_ID (repo root .env).`);
}

function formatAge(ms) {
  if (ms < MS_HOUR) return `${Math.round(ms / 60000)}m`;
  if (ms < MS_DAY) return `${Math.round(ms / MS_HOUR)}h`;
  return `${Math.round(ms / MS_DAY)}d`;
}

function hasCustomDomainAlias(aliases) {
  if (!Array.isArray(aliases) || aliases.length === 0) return false;
  return aliases.some((alias) => {
    try {
      const host = new URL(alias).hostname.toLowerCase();
      return !host.endsWith('.pages.dev');
    } catch {
      return false;
    }
  });
}

function branchOf(deployment) {
  return deployment.deployment_trigger?.metadata?.branch ?? '(no branch)';
}

function isSuccessful(deployment) {
  return deployment.latest_stage?.status === 'success';
}

async function cfFetch(path, { method = 'GET' } = {}) {
  const accountId = process.env.CLOUDFLARE_ACCOUNT_ID;
  const token = process.env.CLOUDFLARE_API_TOKEN;
  if (!accountId || !token) {
    throw new Error('set CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN');
  }
  const url = `https://api.cloudflare.com/client/v4/accounts/${accountId}${path}`;
  const res = await fetch(url, {
    method,
    headers: { Authorization: `Bearer ${token}` },
  });
  const json = await res.json();
  if (!json.success) {
    const msg = json.errors?.map((e) => e.message).join('; ') || `HTTP ${res.status}`;
    throw new Error(msg);
  }
  return json;
}

async function listAllDeployments(projectName, environment) {
  const all = [];
  let page = 1;
  let totalPages = 1;
  while (page <= totalPages) {
    const json = await cfFetch(
      `/pages/projects/${encodeURIComponent(projectName)}/deployments?env=${environment}&page=${page}&per_page=25`,
    );
    all.push(...(json.result ?? []));
    totalPages = json.result_info?.total_pages ?? 1;
    page += 1;
  }
  return all;
}

async function deleteDeployment(projectName, deploymentId) {
  return cfFetch(
    `/pages/projects/${encodeURIComponent(projectName)}/deployments/${deploymentId}`,
    { method: 'DELETE' },
  );
}

function classifyDeployments(deployments, { cutoff, keepLatest, environment }) {
  const now = Date.now();
  const byBranch = new Map();

  for (const d of deployments) {
    const branch = branchOf(d);
    if (!byBranch.has(branch)) byBranch.set(branch, []);
    byBranch.get(branch).push(d);
  }

  for (const list of byBranch.values()) {
    list.sort((a, b) => new Date(b.created_on) - new Date(a.created_on));
  }

  const kept = [];
  const candidates = [];

  for (const d of deployments) {
    const branch = branchOf(d);
    const branchList = byBranch.get(branch) ?? [];
    const rank = branchList.findIndex((x) => x.id === d.id);
    const created = new Date(d.created_on);
    const ageMs = now - created.getTime();
    const aliases = d.aliases ?? [];

    if (hasCustomDomainAlias(aliases)) {
      kept.push({ deployment: d, reason: 'custom-domain alias (live)' });
      continue;
    }

    if (rank >= 0 && rank < keepLatest) {
      kept.push({ deployment: d, reason: `keep-latest per branch (${rank + 1}/${keepLatest})` });
      continue;
    }

    if (created >= cutoff) {
      kept.push({ deployment: d, reason: `newer than threshold (${formatAge(ageMs)})` });
      continue;
    }

    if (environment === 'production' && !isSuccessful(d)) {
      kept.push({ deployment: d, reason: 'production: skip non-success deployment' });
      continue;
    }

    candidates.push({ deployment: d, ageMs, branch, rank });
  }

  return { kept, candidates };
}

function printDeploymentLine(prefix, project, item) {
  const d = item.deployment;
  const age = item.ageMs !== undefined ? formatAge(item.ageMs) : formatAge(Date.now() - new Date(d.created_on).getTime());
  const branch = branchOf(d);
  const url = d.url ?? d.deployment_trigger?.metadata?.branch ?? '';
  console.log(
    `${prefix} project=${project} id=${d.id} branch=${branch} age=${age} url=${url}${item.reason ? ` (${item.reason})` : ''}`,
  );
}

async function main() {
  loadDotEnv();
  const opts = parseArgs(process.argv.slice(2));

  if (opts.help) {
    printHelp();
    return;
  }

  const cutoff = new Date(Date.now() - opts.olderThanMs);
  const mode = opts.apply ? 'APPLY' : 'DRY-RUN';

  console.log(`Cloudflare Pages cleanup [${mode}]`);
  console.log(`  environment : ${opts.env}`);
  console.log(`  projects    : ${opts.projects.join(', ')}`);
  console.log(
    `  threshold   : older than ${opts.profile === 'custom' ? formatAge(opts.olderThanMs) : opts.profile} (${cutoff.toISOString()})`,
  );
  console.log(`  keep-latest : ${opts.keepLatest} per branch`);
  console.log('');

  if (opts.env === 'production' && opts.apply) {
    console.log('WARNING: production cleanup — custom-domain aliases are protected, but review the list below.');
    console.log('');
  }

  let totalCandidates = 0;
  let totalDeleted = 0;
  let totalFailed = 0;

  for (const project of opts.projects) {
    console.log(`--- ${project} (${opts.env}) ---`);
    let deployments;
    try {
      deployments = await listAllDeployments(project, opts.env);
    } catch (err) {
      console.error(`  error listing deployments: ${err.message}`);
      totalFailed += 1;
      continue;
    }

    if (deployments.length === 0) {
      console.log('  (no deployments returned)');
      console.log('');
      continue;
    }

    const { kept, candidates } = classifyDeployments(deployments, {
      cutoff,
      keepLatest: opts.keepLatest,
      environment: opts.env,
    });

    console.log(`  listed: ${deployments.length}, keep: ${kept.length}, delete candidates: ${candidates.length}`);

    for (const item of kept) {
      printDeploymentLine('  [keep]', project, item);
    }

    for (const item of candidates) {
      totalCandidates += 1;
      const prefix = opts.apply ? '  [delete]' : '  [would delete]';
      printDeploymentLine(prefix, project, item);

      if (opts.apply) {
        try {
          await deleteDeployment(project, item.deployment.id);
          totalDeleted += 1;
        } catch (err) {
          totalFailed += 1;
          console.error(`  [failed] id=${item.deployment.id}: ${err.message}`);
        }
      }
    }

    console.log('');
  }

  if (!opts.apply) {
    console.log(`Dry run complete. ${totalCandidates} deployment(s) would be deleted.`);
    console.log('Re-run with --apply to delete.');
  } else {
    console.log(`Done. deleted=${totalDeleted}, failed=${totalFailed}, candidates=${totalCandidates}`);
  }

  if (totalFailed > 0) {
    process.exit(1);
  }
}

main().catch((err) => {
  console.error(`error: ${err.message}`);
  process.exit(1);
});
