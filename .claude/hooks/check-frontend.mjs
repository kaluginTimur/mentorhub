import { readFileSync } from 'node:fs';
import path from 'node:path';
import { readInput, repoRoot, toPosix } from './lib.mjs';

const input = readInput();
const filePath = input.tool_input?.file_path;
if (!filePath) process.exit(0);

const root = repoRoot(filePath);
if (!root) process.exit(0);
const rel = toPosix(path.relative(root, path.resolve(filePath)));
if (!rel.startsWith('frontend/src/') || rel.startsWith('frontend/src/app/core/api/')) process.exit(0);

let text;
try {
  text = readFileSync(filePath, 'utf8');
} catch {
  process.exit(0);
}

const problems = [];

if (/provideApiConfiguration\(\s*['"`]\/['"`]\s*\)/.test(text)) {
  problems.push(
    "provideApiConfiguration('/') produces request URLs like //api/...: the generated client joins rootUrl and a path " +
      "that already starts with /api. Use provideApiConfiguration('').",
  );
}

const isStylesheet = /\.(s?css)$/.test(rel);
const isThemeFile = rel === 'frontend/src/styles/tailwind.css';
if ((rel.startsWith('frontend/src/app/') || rel.startsWith('frontend/src/styles/')) && !isThemeFile) {
  const propertyLiteral =
    /\b(color|background(-color)?|border(-[a-z]+)*|outline(-color)?|fill|stroke|box-shadow|text-shadow|caret-color|accent-color)\s*:\s*[^;"'{}]*?(#[0-9a-fA-F]{3,8}\b|\b(rgba?|hsla?|hwb|lab|lch|oklab|oklch)\()/;
  const stylesheetLiteral = /(^|[\s:,(])#[0-9a-fA-F]{3,8}\b|\b(rgba?|hsla?|hwb|oklch)\(/m;
  if (propertyLiteral.test(text) || (isStylesheet && stylesheetLiteral.test(text))) {
    problems.push(
      'Literal colors in component styles. Use semantic theme tokens (bg-primary, text-foreground, ' +
        'border-border, var(--primary), ...) so a rebrand stays a theme change in frontend/src/styles/tailwind.css.',
    );
  }
  if (/\bfont-family\s*:/.test(text) || /\bfont\s*:\s*[^;]*\b(serif|sans-serif|monospace|["'])/.test(text)) {
    problems.push(
      'Literal font-family in component styles. Use typography tokens (font-sans, font-display, font-mono, ' +
        'var(--font-sans), ...) so fonts stay defined only by the theme.',
    );
  }
}

if (problems.length) {
  process.stdout.write(JSON.stringify({ decision: 'block', reason: `${rel}:\n- ${problems.join('\n- ')}` }));
}
