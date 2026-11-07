// Fails when the production build is missing or contains the development failure switch.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const dir = 'dist/mentorhub-web';
const needle = 'failure-switch';

function* files(d) {
  for (const name of readdirSync(d)) {
    const p = join(d, name);
    if (statSync(p).isDirectory()) yield* files(p);
    else yield p;
  }
}

let found = [];
try {
  found = [...files(dir)].filter((f) => readFileSync(f).includes(needle));
} catch (e) {
  console.error(`Cannot read ${dir}: ${e.message}`);
  process.exit(1);
}
if (found.length) {
  console.error(`The production build contains "${needle}":\n${found.join('\n')}`);
  process.exit(1);
}
