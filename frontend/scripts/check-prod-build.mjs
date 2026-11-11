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

// Fonts and icons are served by the application, never by Google (issue #12).
const external = ['fonts.googleapis.com', 'fonts.gstatic.com'];
const textFiles = [...files(dir)].filter((f) => /\.(html|css|js)$/.test(f));
const offenders = textFiles.filter((f) => {
  const text = readFileSync(f, 'utf8');
  return external.some((host) => text.includes(host));
});
if (offenders.length) {
  console.error(`The production build references Google Fonts:\n${offenders.join('\n')}`);
  process.exit(1);
}

// mat-icon ligatures need this rule: the icon font package declares only the @font-face.
const css = textFiles.filter((f) => f.endsWith('.css')).map((f) => readFileSync(f, 'utf8'));
const iconRule =
  /\.material-symbols-outlined\s*\{[^}]*font-family:\s*["']?Material Symbols Outlined/;
if (!css.some((text) => iconRule.test(text))) {
  console.error('The built CSS has no .material-symbols-outlined rule with the icon font family.');
  process.exit(1);
}
