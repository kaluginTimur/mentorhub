#!/usr/bin/env node
// See the Requirements section of docs/development/docs.md.
import { execFileSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';

const REQUIREMENTS_DIR = 'docs/requirements';
const DECISIONS_DIR = 'docs/architecture/decisions';
const INDEX_FILE = `${REQUIREMENTS_DIR}/README.md`;
const REQUIREMENT_HEADER = /^### ([A-Z]+)-([1-9]\d*) (\S.*)$/;
const SCENARIO_HEADER = /^#### ([A-Z]+)-([1-9]\d*)\.([1-9]\d*) (\S.*?)( \(manual\))?$/;
const ID_LIKE = /([A-Z]+-\d+(?:\.\d+)?)[\s:.\-–—]*(.*)$/;
const TEST_ROOTS = [
  { dir: 'backend/src/test', isTest: (file) => file.endsWith('.java'), scan: scanJava },
  { dir: 'frontend/src', isTest: (file) => file.endsWith('.spec.ts'), scan: scanTypeScript },
];

function usage() {
  console.error('usage: requirements.mjs validate|trace|show <id>|cited-by <prefix or id> [--root <existing directory>]');
  process.exit(2);
}

function repositoryRoot() {
  const i = process.argv.indexOf('--root');
  if (i < 0) return execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();
  const value = process.argv[i + 1];
  if (!value || !existsSync(value)) usage();
  return path.resolve(value);
}

function parseIndex(root, errors) {
  const capabilities = new Map();
  const seenFiles = new Set();
  const text = readFileSync(path.join(root, INDEX_FILE), 'utf8');
  text.split('\n').forEach((line, i) => {
    const target = line.match(/^\|.*\]\(([^)]+\.md)\).*\|/)?.[1];
    if (!target) return;
    const file = path.posix.normalize(target);
    const prefix = line.split('|').map((cell) => cell.trim()).find((cell) => /^[A-Z]+$/.test(cell));
    const where = `${INDEX_FILE}:${i + 1}`;
    if (!prefix) {
      errors.push(`${where}: the row has no prefix`);
    } else if (capabilities.has(prefix)) {
      errors.push(`${where}: prefix ${prefix} is used more than once`);
    } else {
      capabilities.set(prefix, file);
    }
    seenFiles.add(file);
    if (!existsSync(path.join(root, REQUIREMENTS_DIR, file))) errors.push(`${where}: ${file} does not exist`);
  });
  return { capabilities, seenFiles };
}

function suggestion(header, marks) {
  const text = header.replace(/^#+\s*/, '').replace(/^(Requirement|Scenario):\s*/i, '');
  const match = text.match(ID_LIKE);
  return match ? `; did you mean "${marks} ${match[1]} ${match[2]}"?` : '';
}

function parseCapability(root, file, prefix, errors) {
  const relative = `${REQUIREMENTS_DIR}/${file}`;
  const lines = readFileSync(path.join(root, relative), 'utf8').split('\n');
  const requirements = [];
  const sections = new Set();
  const textLines = [];
  let section = null;
  let fenced = false;
  let requirement = null;
  let scenario = null;

  const report = (i, message) => errors.push(`${relative}:${i + 1}: ${message}`);
  const checkPrefix = (i, id, actual) => {
    if (actual !== prefix) report(i, `${id} does not have the prefix ${prefix} of this capability`);
  };

  lines.forEach((line, i) => {
    if (line.startsWith('```')) {
      fenced = !fenced;
      return;
    }
    if (fenced) return;
    if (line.startsWith('## ')) {
      section = line.slice(3).trim();
      sections.add(section);
      requirement = null;
      scenario = null;
      return;
    }
    if (section === 'Requirements' && line.startsWith('### ')) {
      const match = line.match(REQUIREMENT_HEADER);
      scenario = null;
      requirement = null;
      if (!match) {
        report(i, `"${line}" is not a requirement header, expected "### <PREFIX>-<n> <Title>"${suggestion(line, '###')}`);
        return;
      }
      const id = `${match[1]}-${match[2]}`;
      checkPrefix(i, id, match[1]);
      requirement = { id, line: i, statement: false, scenarios: [] };
      requirements.push(requirement);
      textLines.push({ i, text: match[3] });
      return;
    }
    if (section === 'Requirements' && line.startsWith('#### ')) {
      const match = line.match(SCENARIO_HEADER);
      scenario = null;
      if (!match) {
        report(i, `"${line}" is not a scenario header, expected "#### <PREFIX>-<n>.<m> <Title>"${suggestion(line, '####')}`);
        return;
      }
      const id = `${match[1]}-${match[2]}.${match[3]}`;
      checkPrefix(i, id, match[1]);
      if (!requirement || `${match[1]}-${match[2]}` !== requirement.id) {
        report(i, `scenario ${id} is under ${requirement?.id ?? 'no requirement'}`);
      }
      scenario = { id, line: i, manual: Boolean(match[5]), when: false, then: false };
      requirement?.scenarios.push(scenario);
      textLines.push({ i, text: match[4] });
      return;
    }
    textLines.push({ i, text: line });
    if (scenario) {
      if (/^- WHEN\b/.test(line)) scenario.when = true;
      if (/^- THEN\b/.test(line)) scenario.then = true;
    } else if (requirement && /\b(SHALL|MUST)\b/.test(line)) {
      requirement.statement = true;
    }
  });

  for (const name of ['Purpose', 'Requirements']) {
    if (!sections.has(name)) errors.push(`${relative}: missing section "## ${name}"`);
  }
  if (sections.has('Requirements') && requirements.length === 0) errors.push(`${relative}: no requirements`);
  for (const item of requirements) {
    if (!item.statement) report(item.line, `${item.id} has no statement with SHALL or MUST`);
    for (const { id, line, when, then } of item.scenarios) {
      if (!when) report(line, `${id} has no "- WHEN" line`);
      if (!then) report(line, `${id} has no "- THEN" line`);
    }
  }
  return { relative, requirements, textLines };
}

function checkReferences(root, documents, knownIds, prefixes, errors) {
  const reference = new RegExp(`\\b(?:${prefixes.join('|')})-\\d+(?:\\.\\d+)*\\b`, 'g');
  for (const { relative, textLines } of documents) {
    for (const { i, text } of textLines) {
      for (const [id] of text.matchAll(reference)) {
        if (!knownIds.has(id)) errors.push(`${relative}:${i + 1}: the reference ${id} does not exist`);
      }
      for (const [, number] of text.matchAll(/\bdecision (\d{4})\b/gi)) {
        const decisions = path.join(root, DECISIONS_DIR);
        const found = existsSync(decisions) && readdirSync(decisions).some((name) => name.startsWith(`${number}-`));
        if (!found) errors.push(`${relative}:${i + 1}: decision ${number} does not exist`);
      }
    }
  }
}

function loadRequirements(root) {
  const errors = [];
  const empty = { capabilities: new Map(), scenarios: new Map(), knownIds: new Set(), errors };
  const directory = path.join(root, REQUIREMENTS_DIR);
  if (!existsSync(path.join(root, INDEX_FILE))) {
    errors.push(`${INDEX_FILE}: the index is missing`);
    return empty;
  }
  const files = readdirSync(directory).filter((name) => name.endsWith('.md') && name !== 'README.md');

  const { capabilities, seenFiles } = parseIndex(root, errors);
  for (const file of files) {
    if (!seenFiles.has(file)) errors.push(`${REQUIREMENTS_DIR}/${file}: not listed in the index`);
  }

  const documents = [];
  const knownIds = new Set();
  const scenarios = new Map();
  const claim = (id, relative, line) => {
    if (knownIds.has(id)) errors.push(`${relative}:${line + 1}: duplicate id ${id}`);
    knownIds.add(id);
  };
  for (const [prefix, file] of capabilities) {
    if (!existsSync(path.join(directory, file))) continue;
    const document = parseCapability(root, file, prefix, errors);
    documents.push(document);
    for (const requirement of document.requirements) {
      claim(requirement.id, document.relative, requirement.line);
      for (const scenario of requirement.scenarios) {
        claim(scenario.id, document.relative, scenario.line);
        scenarios.set(scenario.id, scenario);
      }
    }
  }
  checkReferences(root, documents, knownIds, [...capabilities.keys()], errors);
  return { capabilities, scenarios, knownIds, errors };
}

function walk(directory) {
  if (!existsSync(directory)) return [];
  return readdirSync(directory).flatMap((name) => {
    const full = path.join(directory, name);
    return statSync(full).isDirectory() ? walk(full) : [full];
  });
}

// Blanks comment lines and block comments, keeping line numbers. Inline trailing comments stay: they can't hold a test name.
function withoutComments(text) {
  let inBlock = false;
  return text.split('\n').map((line) => {
    const trimmed = line.trim();
    if (inBlock) {
      if (trimmed.includes('*/')) inBlock = false;
      return '';
    }
    if (trimmed.startsWith('//')) return '';
    if (trimmed.startsWith('/*')) {
      inBlock = !trimmed.includes('*/');
      return '';
    }
    return line;
  });
}

// Collects each member's run of annotations, so comments and blank lines inside the run don't matter.
function scanJava(text) {
  const found = [];
  let annotations = [];
  let openParens = 0;
  let braces = 0;
  let disabledClass = null;

  withoutComments(text).forEach((line, i) => {
    const code = line.replace(/"(?:[^"\\]|\\.)*"/g, '""').replace(/\/\/.*$/, '').trim();
    if (!code) return;
    const count = (char) => code.split(char).length - 1;
    const inAnnotation = openParens > 0 || code.startsWith('@');
    if (inAnnotation) {
      annotations.push({ line: i, text: line });
      openParens += count('(') - count(')');
    }
    const isType = /(?<![.\w@])(class|interface|enum|record)\s+\w+/.test(code);
    const afterAnnotations = code.replace(/^(?:@[\w.]+(?:\((?:[^()]|\([^()]*\))*\))?\s*)+/, '');
    const isInlineMember = code.startsWith('@') && openParens === 0 && afterAnnotations !== '';
    if ((!inAnnotation || isInlineMember) && annotations.length) {
      const joined = annotations.map((annotation) => annotation.text).join('\n');
      const disabled = /@Disabled\b/.test(joined);
      if (isType && disabled && !disabledClass) disabledClass = { depth: braces, opened: false };
      const name = joined.match(/@DisplayName\(\s*"([^"]*)"/);
      if (name) {
        const nameLine = annotations.find((annotation) => annotation.text.includes('@DisplayName')).line;
        const runs = /@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b/.test(joined);
        found.push({ line: nameLine, name: name[1], onType: isType, skipped: Boolean(disabledClass) || disabled || !runs });
      }
      annotations = [];
    }
    braces += count('{') - count('}');
    if (disabledClass) {
      disabledClass.opened ||= code.includes('{');
      if (disabledClass.opened && braces <= disabledClass.depth) disabledClass = null;
    }
  });
  return found;
}

// Returns the index after the bracket pair that opens at `start`, ignoring brackets inside strings.
function skipBalanced(text, start, open, close) {
  let depth = 0;
  let quote = null;
  for (let i = start; i < text.length; i++) {
    const char = text[i];
    if (quote) {
      if (char === '\\') i++;
      else if (char === quote) quote = null;
    } else if (char === "'" || char === '"' || char === '`') {
      quote = char;
    } else if (char === open) {
      depth++;
    } else if (char === close && --depth === 0) {
      return i + 1;
    }
  }
  return -1;
}

function scanTypeScript(text) {
  const clean = withoutComments(text).join('\n');
  // Matching the enclosing braces is not worth it: a skipped describe discounts the whole file, which fails loudly.
  const describeSkipped = /(?<![.\w$])(?:xdescribe|describe\.skip)\b/.test(clean);
  const skipSpace = (i) => {
    while (/\s/.test(clean[i] ?? '')) i++;
    return i;
  };
  const found = [];
  for (const match of clean.matchAll(/(?<![.\w$])(xit|xtest|it|test)((?:\.\w+)*)/g)) {
    let i = skipSpace(match.index + match[0].length);
    if (/\.(each|for|skipIf|runIf)\b/.test(match[2])) {
      if (clean[i] === '<') i = skipSpace(skipBalanced(clean, i, '<', '>'));
      if (clean[i] !== '(') continue;
      i = skipSpace(skipBalanced(clean, i, '(', ')'));
    }
    if (clean[i] !== '(') continue;
    i = skipSpace(i + 1);
    const quote = clean[i];
    if (quote !== "'" && quote !== '"' && quote !== '`') continue;
    found.push({
      line: clean.slice(0, match.index).split('\n').length - 1,
      name: clean.slice(i + 1, clean.indexOf(quote, i + 1)),
      onType: false,
      skipped: describeSkipped || match[1].startsWith('x') || /\.(skip|todo)\b/.test(match[2]),
    });
  }
  return found;
}

function trace(root, requirements) {
  const errors = [];
  const notes = [];
  const prefixes = [...requirements.capabilities.keys()];
  const covered = new Set();
  if (prefixes.length) {
    const cited = new RegExp(`^((?:${prefixes.join('|')})-\\d+((?:\\.\\d+)*))(?=\\s|$)`);
    for (const { dir, isTest, scan } of TEST_ROOTS) {
      for (const file of walk(path.join(root, dir)).filter(isTest)) {
        const relative = path.relative(root, file).split(path.sep).join('/');
        for (const test of scan(readFileSync(file, 'utf8'))) {
          const match = test.name.match(cited);
          if (!match) continue;
          const [, id, scenarioPart] = match;
          const where = `${relative}:${test.line + 1}`;
          if (test.onType) errors.push(`${where}: ${id} is on a class; a scenario id belongs on a test method`);
          else if (!scenarioPart) errors.push(`${where}: ${id} is a requirement; a test cites a scenario`);
          else if (!requirements.scenarios.has(id)) errors.push(`${where}: the scenario ${id} does not exist`);
          else if (!test.skipped) covered.add(id);
        }
      }
    }
  }
  let automatic = 0;
  let withTest = 0;
  let manual = 0;
  for (const [id, scenario] of requirements.scenarios) {
    if (scenario.manual) {
      manual++;
      if (covered.has(id)) notes.push(`${id} is marked manual but has a test`);
      continue;
    }
    automatic++;
    if (covered.has(id)) withTest++;
    else errors.push(`${id} has no test`);
  }
  return { errors, notes, summary: `${withTest} of ${automatic} scenarios have tests, ${manual} manual` };
}

function finish(errors, notes, success) {
  for (const note of notes) console.log(`requirements: note: ${note}`);
  if (errors.length) {
    for (const error of errors) console.error(`requirements: ${error}`);
    process.exit(1);
  }
  console.log(`requirements: ${success}`);
}

// Prints one requirement with its scenarios, or one scenario.
function show(root, id, capabilities) {
  const file = capabilities.get(id.split('-')[0]);
  const lines = file && existsSync(path.join(root, REQUIREMENTS_DIR, file))
    ? readFileSync(path.join(root, REQUIREMENTS_DIR, file), 'utf8').split('\n')
    : [];
  const marks = id.includes('.') ? '####' : '###';
  const start = lines.findIndex((line) => line.startsWith(`${marks} ${id} `));
  if (start < 0) return null;
  const end = lines.findIndex((line, i) => i > start && (id.includes('.') ? /^#{2,4} / : /^#{2,3} /).test(line));
  return lines.slice(start, end < 0 ? lines.length : end).join('\n').trim();
}

// Lists, per other capability, the ids it cites of a prefix or of one id and its scenarios.
function citedBy(root, target, capabilities) {
  const prefix = target.split('-')[0];
  const id = target.includes('-') ? target.replaceAll('.', '\\.') : `${prefix}-\\d+`;
  const reference = new RegExp(`\\b${id}(?:\\.\\d+)*\\b`, 'g');
  const found = [];
  for (const [other, file] of capabilities) {
    const full = path.join(root, REQUIREMENTS_DIR, file);
    if (other === prefix || !existsSync(full)) continue;
    const ids = [...new Set(readFileSync(full, 'utf8').match(reference) ?? [])];
    if (ids.length) found.push(`${REQUIREMENTS_DIR}/${file}: ${ids.join(', ')}`);
  }
  return found;
}

const command = process.argv[2];
const COMMANDS_WITH_ID = new Set(['show', 'cited-by']);
if (command !== 'validate' && command !== 'trace' && !COMMANDS_WITH_ID.has(command)) usage();
const argument = process.argv[3];
if (COMMANDS_WITH_ID.has(command) && (!argument || argument.startsWith('--'))) usage();
const root = repositoryRoot();
if (COMMANDS_WITH_ID.has(command)) {
  const errors = [];
  const { capabilities } = parseIndex(root, errors);
  if (!capabilities.has(argument.split('-')[0])) {
    console.error(`requirements: unknown prefix in ${argument}`);
    process.exit(1);
  }
  if (command === 'cited-by') {
    console.log(citedBy(root, argument, capabilities).join('\n'));
    process.exit(0);
  }
  const block = show(root, argument, capabilities);
  if (block === null) {
    console.error(`requirements: ${argument} does not exist`);
    process.exit(1);
  }
  console.log(block);
  process.exit(0);
}
const requirements = loadRequirements(root);
if (command === 'validate' || requirements.errors.length) {
  finish(requirements.errors, [], `valid (${requirements.scenarios.size} scenarios)`);
} else {
  const result = trace(root, requirements);
  finish(result.errors, result.notes, result.summary);
}
