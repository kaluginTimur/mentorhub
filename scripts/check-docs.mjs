#!/usr/bin/env node
// See docs/development/docs.md.
import { execFileSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const PLANNED = /\b(if|when|once) it exists\b|\(planned\)/i;
// Files that documents name as a location while their content changes with every feature.
const LOCATION_ONLY = new Set(['api/openapi.yaml']);
const CHECKED_COMMAND_SECTIONS = /^(Verification|Checking an API)\b/;
const READ_ONLY_COMMANDS = ['cd', 'grep', 'cat', 'ls', 'head', 'tail', 'wc', 'echo', 'git status', 'git diff', 'git log', 'git show'];

function git(args, options = {}) {
  try {
    return execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], ...options });
  } catch {
    return null;
  }
}

let root = process.cwd();
root = git(['rev-parse', '--show-toplevel'])?.trim();
if (!root) {
  console.error('check-docs: not inside a git repository');
  process.exit(2);
}

const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const option = (name) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
};

const gitPath = (name) => path.resolve(root, git(['rev-parse', '--git-path', name]).trim());
const MARKER = gitPath('docs-check-base');
const PENDING = gitPath('docs-check-pending');
const readFile = (file) => (existsSync(file) ? readFileSync(file, 'utf8').trim() : null);
const isCommit = (ref) => ref && git(['cat-file', '-e', `${ref}^{commit}`]) !== null;
const head = () => git(['rev-parse', 'HEAD'])?.trim();

if (flag('--mark')) {
  writeFileSync(MARKER, `${head()}\n`);
  console.log(`check-docs: marked ${head().slice(0, 7)} as checked`);
  process.exit(0);
}

if (flag('--mark-if-checked')) {
  // post-commit: advance the marker only if pre-commit checked exactly this tree (not after --no-verify).
  const pending = readFile(PENDING);
  rmSync(PENDING, { force: true });
  if (pending && pending === git(['rev-parse', 'HEAD^{tree}'])?.trim()) writeFileSync(MARKER, `${head()}\n`);
  process.exit(0);
}

const topLevel = new Set(readdirSync(root).filter((name) => name !== '.git'));
const isIgnored = (rel) =>
  [rel, rel.endsWith('/') ? rel.slice(0, -1) : `${rel}/`].some((r) => git(['check-ignore', '-q', '--no-index', r]) !== null);

const knowledgeFiles = git(['ls-files', '--cached', '--others', '--exclude-standard', '--', '*.md'])
  .split('\n')
  .filter(Boolean)
  .filter((f) => /(^|\/)(CLAUDE|README)\.md$/.test(f) || f.startsWith('docs/') || f.startsWith('.claude/'))
  .filter((f) => existsSync(path.join(root, f)));

const broken = [];
const references = new Map();
const sectionRefs = [];
const headings = new Map();
const commands = [];

function resolveRef(raw, docDir) {
  let ref = raw.replace(/#.*$/, '').replace(/:\d+(-\d+)?$/, '');
  if (!ref || /\s|[<>$]|\.\.\./.test(ref) || /^(https?:|~|-|@)/.test(ref)) return null;
  if (!ref.includes('/') && !/\.(md|mjs|js|ts|json|ya?ml|xml|java|sql|scss|html|txt)$/.test(ref)) return null;
  const globAt = ref.split('/').findIndex((segment) => segment.includes('*'));
  const checkable = globAt >= 0 ? ref.split('/').slice(0, globAt).join('/') : ref;
  const candidates = [path.join(root, checkable), path.join(root, docDir, checkable)];
  const found = candidates.find((c) => existsSync(c));
  if (found) {
    const rel = path.relative(root, found).replaceAll('\\', '/');
    return { rel, exists: true, isFile: globAt < 0 && statSync(found).isFile() };
  }
  const first = ref.replace(/^\.\//, '').split('/')[0];
  if (!topLevel.has(first) && !ref.startsWith('../')) return null;
  const rel = path.posix.normalize(ref.startsWith('../') ? path.posix.join(docDir, ref) : ref);
  // A directory may not exist yet (planned, or holding only generated files); its parent must.
  const plannedDir = ref.endsWith('/') && existsSync(path.join(root, path.posix.dirname(rel)));
  return { rel, exists: plannedDir || isIgnored(rel), isFile: false };
}

function addReference(doc, lineNo, line, raw) {
  const docDir = path.posix.dirname(doc);
  const ref = resolveRef(raw, docDir);
  if (!ref) return;
  if (!ref.exists) {
    if (!PLANNED.test(line)) broken.push(`${doc}:${lineNo}: \`${raw}\` does not exist`);
    return;
  }
  if (ref.isFile && !LOCATION_ONLY.has(ref.rel)) {
    if (!references.has(ref.rel)) references.set(ref.rel, []);
    references.get(ref.rel).push(`${doc}:${lineNo}`);
  }
}

for (const doc of knowledgeFiles) {
  const lines = readFileSync(path.join(root, doc), 'utf8').split('\n');
  const isArtifact = doc.startsWith('.claude/agents/') || doc.startsWith('.claude/skills/');
  const docHeadings = [];
  let fence = null;
  let section = '';
  lines.forEach((line, i) => {
    const lineNo = i + 1;
    const fenceMatch = line.match(/^\s*```(\S*)/);
    if (fenceMatch) {
      fence = fence === null ? fenceMatch[1] : null;
      return;
    }
    if (fence !== null) {
      if (fence === 'bash' && doc.startsWith('docs/development/') && CHECKED_COMMAND_SECTIONS.test(section)) {
        if (line.trim() && !line.trim().startsWith('#')) commands.push({ doc, lineNo, command: line.trim() });
      }
      return;
    }
    const heading = line.match(/^#{1,6}\s+(.*)$/);
    if (heading) {
      docHeadings.push(heading[1].trim());
      if (line.startsWith('## ')) section = heading[1].trim();
    }
    const imported = /(^|\/)CLAUDE\.md$/.test(doc) && line.match(/^@(\S+)/);
    if (imported) {
      if (!existsSync(path.join(root, path.posix.dirname(doc), imported[1]))) {
        broken.push(`${doc}:${lineNo}: import \`${imported[1]}\` does not exist`);
      }
    }
    for (const m of line.matchAll(/`([^`\n]+)`/g)) addReference(doc, lineNo, line, m[1]);
    for (const m of line.matchAll(/\]\(([^)\s]+)\)/g)) {
      if (!/^(https?:|#|mailto:)/.test(m[1])) addReference(doc, lineNo, line, m[1]);
    }
    if (isArtifact) {
      for (const m of line.matchAll(/((?:\*\*[^*]+\*\*(?:,\s*|\s+and\s+)?)+)\s+sections?\b(?:\s+of\s+`([^`]+)`)?/g)) {
        for (const name of m[1].matchAll(/\*\*([^*]+)\*\*/g)) {
          sectionRefs.push({ doc, lineNo, name: name[1].trim(), target: m[2] });
        }
      }
    }
  });
  headings.set(doc, docHeadings);
}

const developmentDocs = [...headings.keys()].filter((d) => d.startsWith('docs/development/'));
const hasHeading = (doc, name) => (headings.get(doc) ?? []).some((h) => h === name || h.startsWith(`${name}:`));
for (const ref of sectionRefs) {
  let targets = developmentDocs;
  if (ref.target && !/[<>]/.test(ref.target)) targets = [ref.target];
  const ok = ref.name === 'Verification' && (!ref.target || /[<>]/.test(ref.target))
    ? developmentDocs.every((d) => hasHeading(d, ref.name))
    : targets.some((d) => hasHeading(d, ref.name));
  if (!ok) {
    const where = ref.target && !/[<>]/.test(ref.target) ? ref.target : 'docs/development/';
    broken.push(`${ref.doc}:${ref.lineNo}: section "${ref.name}" not found in ${where}`);
  }
}

const settings = JSON.parse(readFileSync(path.join(root, '.claude/settings.json'), 'utf8'));
const allowRules = (settings.permissions?.allow ?? [])
  .map((r) => r.match(/^Bash\((.*)\)$/)?.[1])
  .filter(Boolean);
const matchesRule = (cmd, rule) => {
  if (rule.endsWith(' *') && !rule.slice(0, -2).includes('*')) {
    const prefix = rule.slice(0, -2);
    return cmd === prefix || cmd.startsWith(`${prefix} `);
  }
  const pattern = rule.split('*').map((s) => s.replace(/[.+?^${}()|[\]\\]/g, '\\$&')).join('.*');
  return new RegExp(`^${pattern}$`).test(cmd);
};
for (const { doc, lineNo, command } of commands) {
  const segments = command.replace(/<[^>]+>/g, 'X').split(/&&|\|\||;|\|/).map((s) => s.trim()).filter(Boolean);
  for (const segment of segments) {
    if (READ_ONLY_COMMANDS.some((c) => segment === c || segment.startsWith(`${c} `))) continue;
    if (!allowRules.some((rule) => matchesRule(segment, rule))) {
      broken.push(`${doc}:${lineNo}: \`${segment}\` matches no allow rule in .claude/settings.json`);
    }
  }
}

for (const hook of readdirSync(path.join(root, '.claude/hooks')).filter((f) => f.endsWith('.mjs'))) {
  const source = readFileSync(path.join(root, '.claude/hooks', hook), 'utf8');
  for (const m of source.matchAll(/['"`]([\w.-]+\/[\w./-]*)['"`]/g)) {
    const rel = m[1];
    if (!topLevel.has(rel.split('/')[0])) continue;
    if (!existsSync(path.join(root, rel)) && !isIgnored(rel)) {
      broken.push(`.claude/hooks/${hook}: protected path \`${rel}\` does not exist`);
    }
  }
}

const lines = (out) => (out ?? '').split('\n').filter(Boolean);
const since = option('--since');
const marker = readFile(MARKER);
let base = since ?? (isCommit(marker) ? marker : null);
if (since && !isCommit(since)) {
  console.error(`check-docs: unknown ref ${since}`);
  process.exit(2);
}
let changed;
let rangeNote;
if (flag('--staged')) {
  changed = lines(git(['diff', '--cached', '--name-only', base ?? 'HEAD']));
  rangeNote = base ? `since ${base.slice(0, 7)} including staged changes` : 'in staged changes (no checked commit recorded yet)';
} else {
  changed = [...lines(git(['diff', '--name-only', base ?? 'HEAD'])), ...lines(git(['ls-files', '--others', '--exclude-standard']))];
  rangeNote = base
    ? `since ${since ? since : `${base.slice(0, 7)} (last checked commit)`} including uncommitted changes`
    : 'in uncommitted changes (no checked commit recorded; pass --since <ref> to look further back)';
}
const changedSet = new Set(changed);

if (broken.length) {
  console.log(`check-docs: ${broken.length} broken reference(s)`);
  broken.forEach((b) => console.log(`  ✗ ${b}`));
}

const knowledgeSet = new Set(knowledgeFiles);
// Links between documents are covered by the broken-reference check, so only code and config changes trigger reminders.
const reminders = [...changedSet].filter((f) => references.has(f) && !knowledgeSet.has(f));
if (reminders.length) {
  console.log(`check-docs: changes ${rangeNote} touch files that documentation refers to; check that it is still accurate:`);
  for (const file of reminders) {
    const byDoc = new Map();
    for (const r of references.get(file)) {
      const [doc, lineNo] = r.split(':');
      byDoc.set(doc, [...(byDoc.get(doc) ?? []), lineNo]);
    }
    const docs = [...byDoc].map(([doc, nums]) => `${doc}:${nums.join(',')}${changedSet.has(doc) ? ' (also changed)' : ''}`);
    console.log(`  ${file} → ${docs.join(', ')}`);
  }
}

if (!broken.length && !reminders.length) console.log(`check-docs: OK (${knowledgeFiles.length} documents, changes ${rangeNote})`);

if (broken.length) process.exit(1);
if (flag('--staged')) writeFileSync(PENDING, `${git(['write-tree']).trim()}\n`);
