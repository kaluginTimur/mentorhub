// Generates the API client from api/openapi.yaml only when the contract, the generator's configuration or its version
// changed since the last run. `start`, `build`, `test` and `lint` all run this first; regenerating every time deleted
// and rewrote src/app/core/api under a running dev server, which then failed to compile when two of them overlapped.
// `--force` regenerates anyway.
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { join } from 'node:path';

const OUTPUT = 'src/app/core/api';
const STAMP = join(OUTPUT, '.contract-hash');
const LOCK = 'node_modules/.cache/generate-api.lock';
const LOCK_WAIT_MS = 120_000;

const hash = createHash('sha256');
for (const file of [
  '../api/openapi.yaml',
  'ng-openapi-gen.json',
  'node_modules/ng-openapi-gen/package.json',
]) {
  hash.update(readFileSync(file));
}
const expected = hash.digest('hex');

const upToDate = () =>
  existsSync(STAMP) &&
  readFileSync(STAMP, 'utf8').trim() === expected &&
  readdirSync(OUTPUT).length > 1;

if (!process.argv.includes('--force') && upToDate()) {
  console.log('API client is up to date with api/openapi.yaml.');
  process.exit(0);
}

// Another run may be generating right now: wait for it, with a limit, then check again. A lock older than the limit
// is left over from a killed run.
mkdirSync('node_modules/.cache', { recursive: true });
const started = Date.now();
for (;;) {
  try {
    mkdirSync(LOCK);
    break;
  } catch {
    let age;
    try {
      age = Date.now() - statSync(LOCK).mtimeMs;
    } catch {
      continue; // The holder removed it in between.
    }
    if (age > LOCK_WAIT_MS) {
      rmSync(LOCK, { recursive: true, force: true });
      continue;
    }
    if (Date.now() - started > LOCK_WAIT_MS) {
      console.error(
        `Another API client generation holds ${LOCK} for over ${LOCK_WAIT_MS / 1000} s.`,
      );
      process.exit(1);
    }
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 500);
  }
}

try {
  if (!process.argv.includes('--force') && upToDate()) {
    console.log('API client is up to date with api/openapi.yaml.');
  } else {
    execFileSync(process.execPath, ['node_modules/ng-openapi-gen/lib/index.js'], {
      stdio: 'inherit',
    });
    writeFileSync(STAMP, `${expected}\n`);
  }
} finally {
  rmSync(LOCK, { recursive: true, force: true });
}
