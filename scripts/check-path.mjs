#!/usr/bin/env node
// Checks that the branch's prefix names a path of docs/process.md (Choosing a path) and that the change holds nothing
// that path does not change. Usage: node scripts/check-path.mjs [<base>]
import { execFileSync } from 'node:child_process';

const MIGRATIONS = 'backend/src/main/resources/db/migration/';
const REQUIREMENTS = 'docs/requirements/';
const CONTRACT = 'api/openapi.yaml';

const STAGES_ONLY = [
  (f) => f === CONTRACT || f.startsWith(MIGRATIONS),
  'the contract or a migration: that is a change for the stages (feature/)',
];
const NO_REQUIREMENTS = [(f) => f.startsWith(REQUIREMENTS), 'a requirement: that is a change of behavior (change/ or feature/)'];

// For each prefix: what a file of the change must not be, and where such a change belongs.
const PATHS = {
  feature: [],
  change: [STAGES_ONLY],
  fix: [NO_REQUIREMENTS, STAGES_ONLY],
  chore: [NO_REQUIREMENTS, STAGES_ONLY],
  explore: [[(f) => !f.startsWith('docs/'), 'a file outside docs/: an exploration leaves findings, not code']],
};

// From the repository root, so that every path git prints starts there, wherever the script is run from.
const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();

function git(args) {
  try {
    return execFileSync('git', ['-C', root, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch {
    return null;
  }
}

function fail(message) {
  console.error(`check-path: ${message}`);
  process.exit(1);
}

const lines = (text) => (text ? text.split('\n').filter(Boolean) : []);

const base = process.argv[2] ?? (git(['rev-parse', '--verify', '-q', 'origin/main']) ? 'origin/main' : 'main');
const branch = git(['symbolic-ref', '--short', '-q', 'HEAD']);
if (!branch) fail('HEAD is not on a branch.');
if (branch === base.replace(/^origin\//, '')) fail(`${branch} is the default branch: a change gets its own branch at Choosing a path.`);

const prefix = branch.includes('/') ? branch.slice(0, branch.indexOf('/')) : '';
const rules = PATHS[prefix];
if (!rules) {
  fail(`the prefix of ${branch} names no path; expected one of ${Object.keys(PATHS).map((p) => `${p}/`).join(', ')}.`);
}

const mergeBase = git(['merge-base', 'HEAD', base]);
if (!mergeBase) fail(`no common commit with ${base}.`);
// Against the working tree, so uncommitted and untracked files count too. Without rename detection, so a file moved out
// of a guarded folder still shows its old path.
const files = [...new Set([...lines(git(['diff', '--name-only', '--no-renames', mergeBase])), ...lines(git(['ls-files', '--others', '--exclude-standard']))])];

const problems = files.flatMap((f) => rules.filter(([matches]) => matches(f)).map(([, why]) => `  ${f}: ${why}`));
if (problems.length) {
  fail(`${branch} holds what a ${prefix}/ change does not:\n${problems.join('\n')}\nReturn to Choosing a path in docs/process.md.`);
}
console.log(`check-path: ${branch} against ${base}: ${files.length} files, nothing outside the path.`);
