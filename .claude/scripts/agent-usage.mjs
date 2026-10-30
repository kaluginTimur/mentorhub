#!/usr/bin/env node
// Token usage of a Claude Code session and its subagents, read from the local transcripts.
// Usage: node .claude/scripts/agent-usage.mjs [session-id | path/to/<session>.jsonl] [--record <feature> <phase> [--until <time>] [--force]]
//   Without a session it reads the latest session of this repository.
//   Claude Code can continue a conversation in a new session (a `continued-in` entry in the old transcript, whose
//   history the new one repeats); the whole chain of such sessions is read as one, each API call counted once.
//   --record appends the chain to .claude/usage/<feature>.jsonl together with the feature's current size, and prints one
//   line per session. One session can hold several phases: /clear keeps the session id in a cloud session. A record
//   therefore covers only the API calls after the chain's latest record of another phase, and replaces an earlier record
//   of the same phase of the chain. --until <time> (ISO 8601) ends the window earlier, to rebuild a missed record. It
//   refuses a chain none of whose agents (in the window) is listed in docs/features/<feature>.progress.md, one that ended
//   before another recorded session of the feature, or (without --until) a phase recorded again after a later phase;
//   --force overrides.
// Transcripts are local to the machine that ran the session and are deleted after cleanupPeriodDays (30 by default);
// the records are what persists.
// Weighted = input-token equivalents at API price ratios: input 1, cache read 0.1, 5-minute write 1.25, 1-hour write 2.
// Output tokens are left out of the weighted total: subagent transcripts do not record them reliably. The main session's
// output is reported on its own. Effort is the reasoning effort the requests ran at, read per agent (a subagent can differ from the session).
// An agent spawned by a subagent (such as api-lookup by backend-dev) gets its own row; spawnedBy names the top-level agent it
// works for, and the summary rolls its cost up to that agent. Inspections counts an agent's tool calls into dependency
// artifacts (javap, unzip -l, jar tf, ~/.m2, node_modules), which implementation agents should leave to api-lookup.
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';

function fail(message) {
  console.error(`agent-usage: ${message}`);
  process.exit(1);
}

function git(args) {
  try {
    return execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch {
    return null;
  }
}

let root = process.cwd();
root = git(['rev-parse', '--show-toplevel']) ?? root;

const args = process.argv.slice(2);
let record = null;
const recordAt = args.indexOf('--record');
if (recordAt >= 0) {
  const [feature, phase] = args.slice(recordAt + 1, recordAt + 3);
  if (!feature || !phase || feature.startsWith('-') || phase.startsWith('-')) fail('--record needs <feature> <phase>');
  record = { feature, phase };
  args.splice(recordAt, 3);
}
let until = null;
const untilAt = args.indexOf('--until');
if (untilAt >= 0) {
  until = args[untilAt + 1];
  if (!record || !until || Number.isNaN(Date.parse(until))) fail('--until needs --record and an ISO 8601 time');
  until = new Date(until).toISOString();
  args.splice(untilAt, 2);
}
const forceAt = args.indexOf('--force');
const force = forceAt >= 0;
if (force) args.splice(forceAt, 1);

const configDir = process.env.CLAUDE_CONFIG_DIR ?? path.join(os.homedir(), '.claude');
const projectsDir = path.join(configDir, 'projects');
// Claude Code keeps a session's transcript in projects/<working directory with every non-alphanumeric character as '-'>;
// sessions started in one of this repository's worktrees (.claude/worktrees/<name>) get their own folder.
const encodedRoot = root.replace(/[^a-zA-Z0-9]/g, '-');
const projectDirs = existsSync(projectsDir)
  ? readdirSync(projectsDir)
      .filter((d) => d === encodedRoot || d.startsWith(`${encodedRoot}--claude-worktrees-`))
      .map((d) => path.join(projectsDir, d))
  : [];
const sessions = () =>
  projectDirs.flatMap((dir) => readdirSync(dir).filter((f) => f.endsWith('.jsonl')).map((f) => path.join(dir, f)));

function sessionFile(arg) {
  if (arg?.endsWith('.jsonl')) {
    if (!existsSync(arg)) fail(`${arg} does not exist`);
    return path.resolve(arg);
  }
  if (projectDirs.length === 0) {
    fail(
      `no Claude Code transcripts for ${root} in ${projectsDir}. Run it on the machine where the sessions ran, ` +
        'set CLAUDE_CONFIG_DIR if Claude Code uses another config directory, or pass the path to a <session>.jsonl file.',
    );
  }
  if (arg) {
    const found = sessions().find((f) => path.basename(f, '.jsonl') === arg);
    if (!found) fail(`session ${arg} not found under ${projectsDir}`);
    return found;
  }
  const latest = sessions().sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs)[0];
  if (!latest) fail(`no sessions found for ${root}`);
  return latest;
}

const readLines = (file) => readFileSync(file, 'utf8').split('\n');
const sessionIdOf = (file) => path.basename(file, '.jsonl');

function continuedIn(file) {
  for (const line of readLines(file)) {
    if (!line.includes('"continued-in"')) continue;
    try {
      const e = JSON.parse(line);
      if (e.type === 'continued-in' && e.continuedInSessionId) return e.continuedInSessionId;
    } catch {}
  }
  return null;
}

// The sessions continued into or from the given one, oldest first.
function chainOf(file) {
  const all = projectDirs.length ? sessions() : [file];
  const byId = new Map(all.map((f) => [sessionIdOf(f), f]));
  byId.set(sessionIdOf(file), file);
  const next = new Map();
  const previous = new Map();
  for (const f of byId.values()) {
    const target = continuedIn(f);
    if (target && byId.has(target)) {
      next.set(sessionIdOf(f), target);
      previous.set(target, sessionIdOf(f));
    }
  }
  let id = sessionIdOf(file);
  const visited = new Set([id]);
  while (previous.has(id) && !visited.has(previous.get(id))) visited.add((id = previous.get(id)));
  const chain = [id];
  while (next.has(id) && !chain.includes(next.get(id))) chain.push((id = next.get(id)));
  return chain.map((x) => byId.get(x));
}

// Tool calls that look into a dependency's artifacts: the work api-lookup exists for.
const dependencyInspection = /\bjavap\b|\bunzip -l\b|\bjar tf\b|dependency:build-classpath|\.m2\/repository|node_modules\//;
const inspectsDependency = (input) =>
  [input?.command, input?.file_path, input?.path, input?.pattern].some((v) => typeof v === 'string' && dependencyInspection.test(v));

function analyse(files, window = {}) {
  const seen = new Set();
  const tools = new Set();
  const models = new Set();
  const efforts = new Set();
  const output = new Map();
  const s = { calls: 0, input: 0, read: 0, write5m: 0, write1h: 0, rebuilds: 0, rebuildTokens: 0, context: 0, inspections: 0, first: null, last: null };
  let prev = null;
  for (const line of files.flatMap(readLines)) {
    if (!line) continue;
    let e;
    try {
      e = JSON.parse(line);
    } catch {
      continue;
    }
    if (e.timestamp && ((window.since && e.timestamp <= window.since) || (window.until && e.timestamp > window.until))) continue;
    for (const m of line.matchAll(/"effort":"(low|medium|high|xhigh|max)"/g)) efforts.add(m[1]);
    const m = e.message;
    // A streamed message is written several times under one id; the last copy has the final output count.
    if (e.type === 'assistant' && m?.usage) output.set(m.id, Math.max(output.get(m.id) ?? 0, m.usage.output_tokens ?? 0));
    // Each content block of a message is written as its own line under the message id, so tool calls count by their own id.
    if (e.type === 'assistant') {
      for (const c of Array.isArray(m?.content) ? m.content : []) {
        if (c.type === 'tool_use' && !tools.has(c.id)) {
          tools.add(c.id);
          if (inspectsDependency(c.input)) s.inspections++;
        }
      }
    }
    if (e.type !== 'assistant' || !m?.usage || seen.has(m.id)) continue;
    seen.add(m.id);
    if (m.model && m.model !== '<synthetic>') models.add(m.model);
    const u = m.usage;
    const write = u.cache_creation_input_tokens ?? 0;
    const w1h = u.cache_creation?.ephemeral_1h_input_tokens ?? 0;
    const w5m = u.cache_creation ? (u.cache_creation.ephemeral_5m_input_tokens ?? 0) : write;
    s.calls++;
    s.input += u.input_tokens ?? 0;
    s.read += u.cache_read_input_tokens ?? 0;
    s.write5m += w5m;
    s.write1h += w1h;
    const ts = Date.parse(e.timestamp);
    // A large write after a pause is the whole history being cached again because the cache expired.
    if (prev !== null && (ts - prev) / 60000 > 5 && write > 0.5 * s.context) {
      s.rebuilds++;
      s.rebuildTokens += write;
    }
    s.context = (u.input_tokens ?? 0) + (u.cache_read_input_tokens ?? 0) + write;
    s.first ??= e.timestamp;
    s.last = e.timestamp;
    prev = ts;
  }
  s.model = [...models].map((x) => x.replace(/^claude-/, '')).join(',');
  s.effort = [...efforts].sort().join(',') || null;
  s.output = [...output.values()].reduce((a, b) => a + b, 0);
  s.avgContext = s.calls ? Math.round((s.input + s.read + s.write5m + s.write1h) / s.calls) : 0;
  s.weighted = Math.round(s.input + 0.1 * s.read + 1.25 * s.write5m + 2 * s.write1h);
  return s;
}

const callIds = (file) => {
  const ids = new Set();
  for (const line of readLines(file)) {
    if (!line.includes('"usage"')) continue;
    try {
      const e = JSON.parse(line);
      if (e.type === 'assistant' && e.message?.usage) ids.add(e.message.id);
    } catch {}
  }
  return ids;
};

// In a cloud session /clear starts a new local session file but the conversation stays in the cloud session's
// transcript, which repeats the new file's calls; subagents started meanwhile can be only in the new file's folder.
// Such fragments (every call also in the chain) are read with the chain.
function withFragments(chain) {
  if (!projectDirs.length) return chain;
  const known = new Set(chain.flatMap((f) => [...callIds(f)]));
  const fragments = sessions().filter((f) => {
    if (chain.includes(f)) return false;
    const ids = callIds(f);
    return ids.size > 0 && [...ids].every((id) => known.has(id));
  });
  return [...chain, ...fragments.sort()];
}

const chain = withFragments(chainOf(sessionFile(args[0])));
const chainIds = chain.map(sessionIdOf);
const sessionId = chainIds[0];

const usageFile = record && path.join(root, '.claude', 'usage', `${record.feature}.jsonl`);
const existing = usageFile && existsSync(usageFile) ? readFileSync(usageFile, 'utf8').split('\n').filter(Boolean).map((l) => [l, JSON.parse(l)]) : [];
const ofChain = (r) => (r.sessions ?? [r.session]).some((id) => chainIds.includes(id));
const recordedAt = until ?? new Date().toISOString();
// The chain's records of other phases that ended before this window; this record starts after the latest of them.
const otherPhases = existing.map(([, r]) => r).filter((r) => r.type === 'size' && ofChain(r) && r.phase !== record.phase);
const since = otherPhases.filter((r) => r.recordedAt < recordedAt).reduce((a, r) => (r.recordedAt > a ? r.recordedAt : a), '') || null;
const window = { since, until };

const rows = [{ agent: 'main', description: 'main session', ...analyse(chain, window) }];
const agentIds = [];
const perSession = chain.map((main) => ({ id: sessionIdOf(main), ...analyse([main], window), agents: 0 }));
// One subagent can have a transcript in several folders of the chain (a fragment's and the cloud session's); its calls
// are counted once, and the fullest meta (the one with a description) names it.
const subagents = new Map();
for (const [i, main] of chain.entries()) {
  const subDir = path.join(main.replace(/\.jsonl$/, ''), 'subagents');
  if (!existsSync(subDir)) continue;
  for (const f of readdirSync(subDir).filter((x) => x.endsWith('.jsonl')).sort()) {
    const metaFile = path.join(subDir, f.replace(/\.jsonl$/, '.meta.json'));
    const meta = existsSync(metaFile) ? JSON.parse(readFileSync(metaFile, 'utf8')) : {};
    const id = f.replace(/^agent-|\.jsonl$/g, '');
    const s = subagents.get(id) ?? { files: [], meta: {}, session: i };
    s.files.push(path.join(subDir, f));
    if (!s.meta.description) s.meta = meta;
    subagents.set(id, s);
  }
}
for (const [id, { files, meta, session }] of subagents) {
  const row = { agent: meta.agentType ?? 'agent', description: meta.description ?? id, id, parentId: meta.parentAgentId ?? null, ...analyse(files, window), output: null };
  if (!row.calls) continue;
  agentIds.push(id);
  rows.push(row);
  perSession[session].agents++;
  if (row.last && (!perSession[session].last || row.last > perSession[session].last)) perSession[session].last = row.last;
}
rows.sort((a, b) => (a.first ?? '').localeCompare(b.first ?? ''));
// An agent spawned by another agent lands in the same subagents folder; its meta names the parent. spawnedBy is the
// top-level agent it ultimately works for, so its cost can be rolled up to that agent.
const byId = new Map(rows.filter((r) => r.id).map((r) => [r.id, r]));
for (const r of rows) {
  let top = r;
  const seen = new Set();
  while (top.parentId && byId.has(top.parentId) && !seen.has(top.parentId)) seen.add((top = byId.get(top.parentId)).id);
  r.spawnedBy = top === r ? null : top.agent;
}
const chainLast = perSession.reduce((a, p) => (p.last && p.last > a ? p.last : a), '');

function featureSize(feature) {
  const branch = `feature/${feature}`;
  const head = git(['rev-parse', '--verify', '-q', branch]) ? branch : 'HEAD';
  const base = git(['symbolic-ref', '--short', 'refs/remotes/origin/HEAD'])?.replace(/^origin\//, '') ?? 'main';
  // Once the branch is merged, its changes are the merge commit's diff against its first parent.
  const merge = head !== 'HEAD' && git(['merge-base', '--is-ancestor', head, base]) !== null
    ? git(['rev-list', '--ancestry-path', '--merges', '--reverse', `${head}..${base}`])?.split('\n')[0]
    : null;
  const stat = (merge ? git(['diff', '--shortstat', `${merge}^1`, merge]) : git(['diff', '--shortstat', `${base}...${head}`])) ?? '';
  const num = (re) => Number(stat.match(re)?.[1] ?? 0);
  const specPath = `docs/features/${feature}.md`;
  const spec = git(['show', `${head}:${specPath}`]) ?? (existsSync(path.join(root, specPath)) ? readFileSync(path.join(root, specPath), 'utf8') : '');
  const acs = new Set([...spec.matchAll(/^- AC-(\d+)/gm)].map((m) => m[1])).size;
  return { base, head, files: num(/(\d+) files? changed/), insertions: num(/(\d+) insertions?/), deletions: num(/(\d+) deletions?/), acs };
}

const k = (n) => (n >= 1e6 ? `${(n / 1e6).toFixed(2)}M` : `${Math.round(n / 1000)}k`);

if (record) {
  const file = usageFile;
  mkdirSync(path.dirname(file), { recursive: true });
  const kept = existing.filter(([, r]) => !ofChain(r) || r.phase !== record.phase).map(([l]) => l);

  const progressLog = `docs/features/${record.feature}.progress.md`;
  const problems = [];
  // /feature lists every agent it starts in the progress log's Agents table.
  const log = git(['show', `${featureSize(record.feature).head}:${progressLog}`]) ?? (existsSync(path.join(root, progressLog)) ? readFileSync(path.join(root, progressLog), 'utf8') : '');
  if (!agentIds.some((id) => log.includes(id))) problems.push(`none of its agents is in the Agents table of ${progressLog}`);
  const later = existing.map(([, r]) => r).filter((r) => r.type === 'size' && !ofChain(r) && r.recordedAt > chainLast);
  if (later.length) problems.push(`it ended (${chainLast}) before ${record.feature}/${later[0].phase} was recorded (${later[0].recordedAt})`);
  const samePhase = existing.map(([, r]) => r).filter((r) => r.type === 'size' && ofChain(r) && r.phase === record.phase);
  const after = !until && otherPhases.find((r) => samePhase.some((p) => p.recordedAt < r.recordedAt) || r.recordedAt >= recordedAt);
  if (after) problems.push(`${record.feature}/${after.phase} of this session was recorded (${after.recordedAt}) after ${record.phase}; recording ${record.phase} again would mix the phases`);
  if (problems.length && !force) fail(`refusing to record session ${chainIds.join(' -> ')}: ${problems.join('; ')}. Pass --force if it is right.`);

  const base = { feature: record.feature, phase: record.phase, session: sessionId, sessions: chainIds, since, recordedAt };
  const lines = rows.map(({ first, last, context, ...r }) => JSON.stringify({ type: 'agent', ...base, ...r, lastContext: context, start: first, end: last }));
  lines.push(JSON.stringify({ type: 'size', ...base, ...featureSize(record.feature) }));
  writeFileSync(file, [...kept, ...lines].join('\n') + '\n');
  const total = rows.reduce((a, r) => a + r.weighted, 0);
  console.log(`agent-usage: recorded ${rows.length} agents (${k(total)} weighted) as ${record.feature}/${record.phase} in ${path.relative(root, file)}:`);
  for (const p of perSession.filter((p) => p.first || p.agents)) console.log(`  session ${p.id}  ${p.first ?? '-'} to ${p.last ?? '-'}  ${p.agents} subagents`);
  if (since) console.log(`  calls after ${since}, the session's record of ${otherPhases.find((r) => r.recordedAt === since).phase}`);
  process.exit(0);
}

console.log(`Session ${chainIds.join(' -> ')}\n`);
const header = ['agent', 'model', 'effort', 'calls', 'avg ctx', 'last ctx', 'read', 'write 5m', 'write 1h', 'weighted', 'inspections', 'rebuilds'];
const table = rows.map((r) => [
  `${r.spawnedBy ? `  ${r.agent} (via ${r.spawnedBy})` : r.agent}: ${r.description}`.slice(0, 48),
  r.model,
  r.effort ?? '-',
  String(r.calls),
  k(r.avgContext),
  k(r.context),
  k(r.read),
  k(r.write5m),
  k(r.write1h),
  k(r.weighted),
  String(r.inspections),
  r.rebuilds ? `${r.rebuilds}: ${k(r.rebuildTokens)} (${Math.round((100 * (r.write1h > r.write5m ? 2 : 1.25) * r.rebuildTokens) / r.weighted)}%)` : '-',
]);
const widths = header.map((h, i) => Math.max(h.length, ...table.map((row) => row[i].length)));
for (const row of [header, ...table]) console.log(row.map((c, i) => (i < 3 ? c.padEnd(widths[i]) : c.padStart(widths[i]))).join('  '));

const byModel = {};
for (const r of rows) byModel[r.model] = (byModel[r.model] ?? 0) + r.weighted;
console.log(`\nMain session output: ${k(rows[0].output)} tokens (${Math.round(rows[0].output / Math.max(rows[0].calls, 1))} per call); not in the weighted totals.`);
console.log(`Weighted by model: ${Object.entries(byModel).map(([m, w]) => `${m} ${k(w)}`).join(', ')}`);
if (rows.some((r) => r.spawnedBy)) {
  const byTop = {};
  for (const r of rows) byTop[r.spawnedBy ?? r.agent] = (byTop[r.spawnedBy ?? r.agent] ?? 0) + r.weighted;
  console.log(`Weighted by top-level agent, nested agents included: ${Object.entries(byTop).map(([a, w]) => `${a} ${k(w)}`).join(', ')}`);
}
console.log('Rebuilds: whole-history cache writes after a pause longer than the cache lifetime (count: tokens, share of the agent\'s weighted cost).');
