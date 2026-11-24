#!/usr/bin/env node
// Brings up the local stack (backend on :8080, frontend dev server on :4200) for browser checks, and stops what it
// started. Run it in the foreground: it returns once both answer, or fails within a time limit with the log's tail.
// Each service it starts runs in its own process group, recorded under .screens/stack/ with the token of the run that
// started it; `stop <token>` stops only the groups of that run, never a process found by name or port, so one agent
// cannot stop a stack another agent or the owner is using. A service that already answers is used as it is when it runs
// from this checkout; `start` refuses one from another live checkout, and stops one left by a deleted worktree under
// .claude/worktrees/, the only service it stops without having started it. The Docker services that Spring Boot starts
// from infra/compose.yaml stay up, as after a person's run.
//
//   node scripts/stack.mjs start          start what is not running, wait until both answer, print the run's token
//   node scripts/stack.mjs status         whether each answers, which run started it, or which checkout it runs from
//   node scripts/stack.mjs stop <token>   stop what the run with that token started
import { execFileSync, spawn } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { existsSync, mkdirSync, openSync, readFileSync, readlinkSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { homedir, uptime } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dir = path.join(root, '.screens', 'stack');
const lock = path.join(dir, 'start.lock');
const nvmExec = path.join(homedir(), '.nvm', 'nvm-exec');
const LOCK_WAIT = 420;
// Boot time is derived from the clock and the uptime, and the WSL clock jumps after the host sleeps.
const BOOT_TOLERANCE = 600;

const SERVICES = [
  {
    name: 'backend',
    cwd: path.join(root, 'backend'),
    command: 'exec ./mvnw spring-boot:run',
    url: 'http://localhost:8080/actuator/health',
    port: 8080,
    healthy: (status, body) => status === 200 && body.includes('"UP"'),
    limit: 300,
    ready: /Started \S+ in [\d.]+ seconds/g,
    failed: /APPLICATION FAILED TO START|BUILD FAILURE|\[ERROR\]/g,
  },
  {
    name: 'frontend',
    cwd: path.join(root, 'frontend'),
    command: existsSync(nvmExec) ? `exec ${nvmExec} npm start` : 'exec npm start',
    url: 'http://localhost:4200',
    port: 4200,
    healthy: (status) => status === 200,
    limit: 240,
    // The dev server keeps serving the last good bundle after a failed one, so an answer alone does not mean it is up.
    ready: /Application bundle generation complete/g,
    failed: /Application bundle generation failed|✘ \[ERROR\]|npm ERR!|npm error/g,
  },
];

const stateFile = (s) => path.join(dir, `${s.name}.json`);
const logFile = (s) => path.join(dir, `${s.name}.log`);
const rel = (p) => path.relative(root, p);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const bootTime = () => Math.round(Date.now() / 1000 - uptime());

function alive(pid, group = false) {
  try {
    process.kill(group ? -pid : pid, 0);
    return true;
  } catch {
    return false;
  }
}

// The start time of a process, to tell our group leader from another process that later got the same id.
function processStart(pid) {
  try {
    return execFileSync('ps', ['-o', 'lstart=', '-p', String(pid)], { encoding: 'utf8' }).trim() || null;
  } catch {
    return null;
  }
}

function readState(s) {
  try {
    return JSON.parse(readFileSync(stateFile(s), 'utf8'));
  } catch {
    return null;
  }
}

// The recorded group, if it is still ours: recorded since the last boot, alive, and its leader, if it has not exited,
// started when we recorded it. A group whose leader exited keeps its id, so no other process can have taken it.
function owned(s) {
  const state = readState(s);
  if (!state || Math.abs(bootTime() - state.boot) > BOOT_TOLERANCE || !alive(state.pgid, true)) return null;
  const leader = processStart(state.pgid);
  return leader === null || leader === state.leaderStart ? state : null;
}

async function answers(s) {
  try {
    const res = await fetch(s.url, { signal: AbortSignal.timeout(3000) });
    return s.healthy(res.status, await res.text());
  } catch {
    return false;
  }
}

function log(s) {
  return existsSync(logFile(s)) ? readFileSync(logFile(s), 'utf8') : '';
}

const lastIndex = (re, text) => Math.max(-1, ...[...text.matchAll(re)].map((m) => m.index));

// The log of a service this script started reports a failure after its last success.
const logFailed = (s) => lastIndex(s.failed, log(s)) > lastIndex(s.ready, log(s));

function tail(s, lines = 30) {
  return existsSync(logFile(s)) ? log(s).split('\n').slice(-lines).join('\n') : '(no log)';
}

// The process listening on the service's port and its working directory, when lsof can tell.
function listener(s) {
  try {
    const pid = execFileSync('lsof', ['-wnP', `-iTCP:${s.port}`, '-sTCP:LISTEN', '-t'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).split('\n')[0].trim();
    const out = execFileSync('lsof', ['-wa', '-p', pid, '-d', 'cwd', '-Fn'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
    const cwd = out.split('\n').find((l) => l.startsWith('n'))?.slice(1);
    return cwd ? { pid: Number(pid), cwd } : null;
  } catch {
    return null;
  }
}

// Where a running service comes from. Worktrees under .claude/worktrees/ lie inside this checkout's folder but are
// other checkouts. A deleted one (lsof marks the directory "(deleted)") serves code nobody can be working on.
function origin(s) {
  const found = listener(s);
  if (found === null) return { kind: 'unknown', text: 'checkout unknown: check that it serves this checkout' };
  const cwd = found.cwd.replace(/ \(deleted\)$/, '');
  const worktrees = path.join(root, '.claude', 'worktrees') + path.sep;
  const inside = cwd === root || cwd.startsWith(root + path.sep);
  if (inside && !cwd.startsWith(worktrees)) return { kind: 'this', text: `runs from this checkout (${cwd})` };
  // The path exactly as lsof gave it: a live folder whose name ends in " (deleted)" exists, a removed one does not.
  if (cwd.startsWith(worktrees) && !existsSync(found.cwd)) {
    return { kind: 'deleted', text: `runs from a deleted worktree, ${cwd}`, pid: found.pid, cwd };
  }
  return { kind: 'other', text: `runs from ANOTHER checkout, ${cwd}: it does not serve this checkout's code` };
}

function groupOf(pid) {
  try {
    return Number(execFileSync('ps', ['-o', 'pgid=', '-p', String(pid)], { encoding: 'utf8' }).trim()) || null;
  } catch {
    return null;
  }
}

// The working directory of a process, as the kernel gives it: a removed one ends in " (deleted)".
function cwdOf(pid) {
  try {
    return readlinkSync(`/proc/${pid}/cwd`);
  } catch {
    return null;
  }
}

// Stops a service left running by a deleted worktree, and waits until its port is free. Its whole process group only
// when every member runs from that worktree (a server started by stack.mjs or a terminal has a group of its own; one
// started from a script or an IDE may share the group with unrelated processes); otherwise the listener alone. Never
// group 0 or 1, nor this script's own group.
async function stopDeleted(s, { pid, cwd }) {
  const pgid = groupOf(pid);
  let members = [];
  try {
    members = execFileSync('ps', ['-o', 'pid=', '-g', String(pgid)], { encoding: 'utf8' }).split('\n').map((l) => l.trim()).filter(Boolean);
  } catch {
    // The group is gone, or ps cannot list it: signal the listener alone.
  }
  const fromWorktree = (p) => {
    const dir = cwdOf(p)?.replace(/ \(deleted\)$/, '');
    return dir !== undefined && (dir === cwd || dir.startsWith(cwd + path.sep));
  };
  const whole = pgid !== null && pgid > 1 && pgid !== groupOf(process.pid) && members.length > 0 && members.every(fromWorktree);
  const signal = (sig) => {
    try {
      process.kill(whole ? -pgid : pid, sig);
    } catch {
      // Already gone.
    }
  };
  signal('SIGTERM');
  for (let i = 0; i < 40 && listener(s) !== null; i++) await sleep(500);
  if (listener(s) !== null) signal('SIGKILL');
  for (let i = 0; i < 10 && listener(s) !== null; i++) await sleep(500);
  return listener(s) === null;
}

function launch(s, token) {
  mkdirSync(dir, { recursive: true });
  const out = openSync(logFile(s), 'w');
  const child = spawn('sh', ['-c', s.command], { cwd: s.cwd, detached: true, stdio: ['ignore', out, out] });
  const state = { pgid: child.pid, token, leaderStart: processStart(child.pid), boot: bootTime() };
  writeFileSync(stateFile(s), `${JSON.stringify(state)}\n`);
  child.unref();
  return child.pid;
}

async function stopGroup(s) {
  const state = owned(s);
  if (state) {
    process.kill(-state.pgid, 'SIGTERM');
    for (let i = 0; i < 40 && alive(state.pgid, true); i++) await sleep(500);
    if (alive(state.pgid, true)) process.kill(-state.pgid, 'SIGKILL');
  }
  rmSync(stateFile(s), { force: true });
  return state !== null;
}

// One `start` at a time: two would both launch a service that does not answer yet. A later one waits for the earlier
// one, then finds the services up and uses them.
async function acquireLock() {
  mkdirSync(dir, { recursive: true });
  const begin = Date.now();
  for (;;) {
    try {
      mkdirSync(lock);
      writeFileSync(path.join(lock, 'pid'), `${process.pid}\n`);
      process.on('exit', () => rmSync(lock, { recursive: true, force: true }));
      return;
    } catch {
      let holder = NaN;
      try {
        holder = Number(readFileSync(path.join(lock, 'pid'), 'utf8'));
      } catch {
        // Just created, the pid not written yet; or just removed.
      }
      let age = 0;
      try {
        age = Date.now() - statSync(lock).mtimeMs;
      } catch {
        continue; // Removed in between.
      }
      // A holder that died, or one killed before it wrote its pid.
      if ((Number.isInteger(holder) && holder > 0 && !alive(holder)) || (Number.isNaN(holder) && age > 10_000)) {
        rmSync(lock, { recursive: true, force: true });
        continue;
      }
      if (Date.now() - begin > LOCK_WAIT * 1000) {
        console.error(`Another \`start\` (pid ${holder}) has run for over ${LOCK_WAIT} s. A broken environment: report it with this output.`);
        process.exit(1);
      }
      await sleep(1000);
    }
  }
}

async function start() {
  await acquireLock();
  const token = randomBytes(3).toString('hex');
  const mine = [];
  const waiting = [];
  for (const s of SERVICES) {
    const state = owned(s);
    if (state && logFailed(s)) {
      console.error(`${s.name}: started by run ${state.token}, but its last build failed. Last lines of ${rel(logFile(s))}:\n${tail(s)}`);
      process.exit(1);
    }
    const up = await answers(s);
    const from = !state && up ? origin(s) : null;
    if (from?.kind === 'deleted') {
      console.log(`${s.name}: ${from.text}; stopping it`);
      if (!(await stopDeleted(s, from))) {
        console.error(`${s.name}: could not stop the service of the deleted worktree on port ${s.port}. A broken environment: report it with this output.`);
        process.exit(1);
      }
    } else if (from?.kind === 'other') {
      console.error(`${s.name}: ${from.text}. Ask the owner to stop it, or to run against that checkout; do not stop it yourself.`);
      process.exit(1);
    }
    if (from?.kind !== 'deleted' && up) {
      console.log(`${s.name}: already running, ${state ? `started by run ${state.token}` : from.text}; ${s.url}`);
    } else if (state) {
      console.log(`${s.name}: started by run ${state.token}, still starting; log ${rel(logFile(s))}`);
      waiting.push(s);
    } else {
      console.log(`${s.name}: starting, process group ${launch(s, token)}; log ${rel(logFile(s))}`);
      mine.push(s);
      waiting.push(s);
    }
  }

  const begin = Date.now();
  while (waiting.length) {
    await sleep(2000);
    const elapsed = (Date.now() - begin) / 1000;
    for (const s of [...waiting]) {
      const reason =
        owned(s) === null
          ? 'the process exited'
          : logFailed(s)
            ? 'the log reports a failure'
            : (await answers(s))
              ? null
              : elapsed > s.limit
                ? `no answer within ${s.limit} s`
                : undefined;
      if (reason === null) {
        console.log(`${s.name}: up after ${Math.round(elapsed)} s; ${s.url}`);
        waiting.splice(waiting.indexOf(s), 1);
      } else if (reason) {
        console.error(`${s.name}: failed to start: ${reason}. Last lines of ${rel(logFile(s))}:\n${tail(s)}`);
        for (const t of mine) await stopGroup(t);
        console.error('Stopped what this run started. A broken environment: report it with this output; do not work around it.');
        process.exit(1);
      }
    }
  }
  console.log(
    mine.length
      ? `Stack is up. Run token: ${token}. When done, stop what this run started with \`node scripts/stack.mjs stop ${token}\`.`
      : 'Stack is up. This run started nothing, so it has nothing to stop.',
  );
}

async function status() {
  for (const s of SERVICES) {
    const up = await answers(s);
    const state = owned(s);
    const who = state ? `started by run ${state.token} (process group ${state.pgid})` : up ? origin(s).text : null;
    console.log(`${s.name}: ${up ? 'answers' : 'does not answer'}${who ? `, ${who}` : ''}; ${s.url}`);
  }
}

async function stop() {
  const token = process.argv[3];
  if (!token) {
    console.error('Usage: node scripts/stack.mjs stop <token>, the token `start` printed; `status` shows which run started what.');
    process.exit(2);
  }
  for (const s of SERVICES) {
    const state = readState(s);
    if (state?.token !== token) {
      console.log(`${s.name}: not started by run ${token}; left as it is`);
    } else {
      console.log(`${s.name}: ${(await stopGroup(s)) ? 'stopped' : 'had already exited'}`);
    }
  }
}

const commands = { start, status, stop };
const command = commands[process.argv[2]];
if (!command) {
  console.error('Usage: node scripts/stack.mjs start | status | stop <token>');
  process.exit(2);
}
await command();
