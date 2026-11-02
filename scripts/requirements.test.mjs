import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const TOOL = fileURLToPath(new URL('./requirements.mjs', import.meta.url));

const INDEX = `# Requirements

| Capability | Prefix |
|---|---|
| [Invitations](invitations.md) | INV |
`;

const REQUIREMENT = `### INV-1 Create an invitation

The system SHALL create an invitation for a new email.

#### INV-1.1 An administrator invites a new person

- WHEN an administrator submits a new email
- THEN an invitation is created
`;

const capability = (requirements = REQUIREMENT, rest = '') => `# Invitations

## Purpose

Let administrators invite people.

## Requirements

${requirements}
${rest}`;

function run(command, files) {
  const root = mkdtempSync(path.join(tmpdir(), 'requirements-'));
  try {
    for (const [file, content] of Object.entries(files)) {
      const target = path.join(root, file);
      mkdirSync(path.dirname(target), { recursive: true });
      writeFileSync(target, content);
    }
    const result = spawnSync(process.execPath, [TOOL, command, '--root', root], { encoding: 'utf8' });
    return { status: result.status, output: result.stdout + result.stderr };
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

const documents = (content = capability(), index = INDEX) => ({
  'docs/requirements/README.md': index,
  'docs/requirements/invitations.md': content,
});

function assertFails(result, expected) {
  assert.equal(result.status, 1, result.output);
  assert.match(result.output, expected);
}

const COVERING_SPEC = `describe('invitations', () => {
  it('INV-1.1 an administrator invites a new person', () => {});
});
`;

test('an unknown command is a usage error', () => {
  assert.equal(run('nonsense', {}).status, 2);
});

test('validate passes when there are no requirements yet', () => {
  assert.equal(run('validate', {}).status, 0);
});

test('validate passes a valid capability with optional sections and references', () => {
  const second = `### INV-2 Revoke an invitation

The system MUST revoke a pending invitation (INV-1, decision 0003).

#### INV-2.1 An administrator revokes

- GIVEN a pending invitation (INV-1.1)
- WHEN an administrator revokes it
- THEN the link stops working
- AND the invitation shows as revoked
`;
  const content = capability(`${REQUIREMENT}\n${second}`, '## Known limitations\n\nNone.\n\n## Out of scope\n\nNone.\n');
  const result = run('validate', {
    ...documents(content),
    'docs/architecture/decisions/0003-external-identity-provider.md': '# 0003\n',
  });
  assert.equal(result.status, 0, result.output);
});

test('validate ignores fenced code', () => {
  const content = capability(`${REQUIREMENT}\n\`\`\`\n### not a header\nsee INV-99\n\`\`\`\n`);
  const result = run('validate', documents(content));
  assert.equal(result.status, 0, result.output);
});

test('validate rejects a capability file missing from the index', () => {
  assertFails(
    run('validate', { ...documents(), 'docs/requirements/audit.md': capability() }),
    /audit\.md.*not listed in the index/,
  );
});

test('validate rejects capability files without an index', () => {
  assertFails(run('validate', { 'docs/requirements/invitations.md': capability() }), /README\.md.*missing/);
});

test('validate rejects an index row whose file does not exist', () => {
  const index = `${INDEX}| [Audit](audit.md) | AUD |\n`;
  assertFails(run('validate', documents(capability(), index)), /audit\.md.*does not exist/);
});

test('validate rejects a prefix used twice in the index', () => {
  const index = `${INDEX}| [Audit](audit.md) | INV |\n`;
  const files = { ...documents(capability(), index), 'docs/requirements/audit.md': capability() };
  assertFails(run('validate', files), /prefix INV.*more than once/);
});

test('validate rejects an id with the prefix of another capability', () => {
  const content = capability(REQUIREMENT.replaceAll('INV-', 'AUD-'));
  assertFails(run('validate', documents(content)), /AUD-1.*prefix INV/);
});

test('validate rejects a duplicate requirement id', () => {
  assertFails(run('validate', documents(capability(`${REQUIREMENT}\n${REQUIREMENT}`))), /INV-1 .*duplicate|duplicate.*INV-1\b/);
});

test('validate rejects a duplicate scenario id', () => {
  const content = capability(`${REQUIREMENT}\n#### INV-1.1 Again\n\n- WHEN x\n- THEN y\n`);
  assertFails(run('validate', documents(content)), /duplicate.*INV-1\.1/);
});

test('validate rejects a requirement without SHALL or MUST', () => {
  const content = capability(REQUIREMENT.replace('SHALL', 'should'));
  assertFails(run('validate', documents(content)), /INV-1.*SHALL or MUST/);
});

test('validate does not take SHALL inside a scenario for the requirement statement', () => {
  const content = capability(`### INV-1 Create

No statement here.

#### INV-1.1 Scenario

- WHEN x
- THEN the system SHALL y
`);
  assertFails(run('validate', documents(content)), /INV-1.*SHALL or MUST/);
});

test('validate rejects a requirement without scenarios', () => {
  const content = capability('### INV-1 Create\n\nThe system SHALL create.\n');
  assertFails(run('validate', documents(content)), /INV-1.*no scenarios/);
});

test('validate rejects a scenario without WHEN or THEN', () => {
  assertFails(run('validate', documents(capability(REQUIREMENT.replace('- WHEN', '- When')))), /INV-1\.1.*WHEN/);
  assertFails(run('validate', documents(capability(REQUIREMENT.replace('- THEN', '- Then')))), /INV-1\.1.*THEN/);
});

test('validate requires WHEN and THEN on a manual scenario too', () => {
  const content = capability('### INV-1 Create\n\nThe system SHALL create.\n\n#### INV-1.1 By hand (manual)\n\nLook at it.\n');
  assertFails(run('validate', documents(content)), /INV-1\.1.*WHEN/);
});

test('validate rejects a scenario under the wrong requirement', () => {
  const content = capability(REQUIREMENT.replace('#### INV-1.1', '#### INV-2.1'));
  assertFails(run('validate', documents(content)), /INV-2\.1.*under INV-1/);
});

test('validate rejects a near-miss requirement header and suggests the form', () => {
  const content = capability(REQUIREMENT.replace('### INV-1 Create', '### Requirement: INV-1: Create'));
  assertFails(run('validate', documents(content)), /did you mean "### INV-1 Create an invitation"/);
});

test('validate rejects a near-miss scenario header and suggests the form', () => {
  const content = capability(REQUIREMENT.replace('#### INV-1.1 An', '#### Scenario: INV-1.1 - An'));
  assertFails(run('validate', documents(content)), /did you mean "#### INV-1\.1 An administrator invites a new person"/);
});

test('validate rejects a number with a leading zero', () => {
  const content = capability(REQUIREMENT.replaceAll('INV-1', 'INV-01'));
  assertFails(run('validate', documents(content)), /INV-01/);
});

test('validate rejects a reference to a missing requirement or scenario', () => {
  assertFails(run('validate', documents(capability(REQUIREMENT, '## Out of scope\n\nSee (INV-9).\n'))), /INV-9.*does not exist/);
  assertFails(run('validate', documents(capability(REQUIREMENT, '## Out of scope\n\nSee (INV-1.9).\n'))), /INV-1\.9.*does not exist/);
});

test('validate resolves references across capabilities', () => {
  const index = `${INDEX}| [Audit](audit.md) | AUD |\n`;
  const audit = capability(REQUIREMENT.replaceAll('INV-', 'AUD-').replace('an invitation is created', 'see INV-1.1'));
  const result = run('validate', { ...documents(capability(), index), 'docs/requirements/audit.md': audit });
  assert.equal(result.status, 0, result.output);
});

test('validate rejects a reference to a missing decision', () => {
  assertFails(run('validate', documents(capability(REQUIREMENT, '## Out of scope\n\nSee (decision 9999).\n'))), /decision 9999.*does not exist/);
});

test('validate rejects a file without Purpose or without requirements', () => {
  assertFails(run('validate', documents(capability().replace('## Purpose', '## Goal'))), /missing.*Purpose/);
  assertFails(run('validate', documents('# Invitations\n\n## Purpose\n\nWhy.\n\n## Requirements\n')), /no requirements/);
});

test('trace passes when every scenario has a frontend test', () => {
  const result = run('trace', { ...documents(), 'frontend/src/app/invitations.spec.ts': COVERING_SPEC });
  assert.equal(result.status, 0, result.output);
  assert.match(result.output, /1 of 1/);
});

test('trace passes when every scenario has a backend test', () => {
  const java = `class InvitationTests {
    @Test
    @DisplayName("INV-1.1 an administrator invites a new person")
    void invites() {}
}
`;
  const result = run('trace', { ...documents(), 'backend/src/test/java/InvitationTests.java': java });
  assert.equal(result.status, 0, result.output);
});

test('trace counts a backend test whose annotations span lines or come after the name', () => {
  const java = `class InvitationTests {
    @DisplayName("INV-1.1 an administrator invites a new person")
    @ParameterizedTest
    @CsvSource({
        "a, b",
    })
    void invites(String a, String b) {}
}
`;
  const result = run('trace', { ...documents(), 'backend/src/test/java/InvitationTests.java': java });
  assert.equal(result.status, 0, result.output);
});

test('trace counts it.each', () => {
  const spec = `it.each([['a'], ['b']])('INV-1.1 invites %s', () => {});\n`;
  const result = run('trace', { ...documents(), 'frontend/src/a.spec.ts': spec });
  assert.equal(result.status, 0, result.output);
});

test('trace fails on a scenario without a test', () => {
  assertFails(run('trace', documents()), /INV-1\.1.*no test/);
});

test('trace does not require a test for a manual scenario', () => {
  const content = capability(REQUIREMENT.replace('new person\n', 'new person (manual)\n'));
  const result = run('trace', documents(content));
  assert.equal(result.status, 0, result.output);
});

test('trace reports but does not fail a manual scenario that has a test', () => {
  const content = capability(REQUIREMENT.replace('new person\n', 'new person (manual)\n'));
  const result = run('trace', { ...documents(content), 'frontend/src/a.spec.ts': COVERING_SPEC });
  assert.equal(result.status, 0, result.output);
  assert.match(result.output, /INV-1\.1.*manual.*has a test/);
});

test('trace fails on a test citing a scenario that does not exist', () => {
  const spec = `${COVERING_SPEC}it('INV-1.2 removed', () => {});\n`;
  assertFails(run('trace', { ...documents(), 'frontend/src/a.spec.ts': spec }), /a\.spec\.ts:\d+.*INV-1\.2.*does not exist/);
});

test('trace fails on a test citing a requirement instead of a scenario', () => {
  const spec = `${COVERING_SPEC}it('INV-1 too broad', () => {});\n`;
  assertFails(run('trace', { ...documents(), 'frontend/src/a.spec.ts': spec }), /a\.spec\.ts:\d+.*INV-1\b.*scenario/);
});

test('trace ignores names with prefixes outside the index and names without ids', () => {
  const spec = `${COVERING_SPEC}it('ABC-1.1 not ours', () => {});\nit('HTTP-2 is supported', () => {});\nit('a whole journey', () => {});\n`;
  const result = run('trace', { ...documents(), 'frontend/src/a.spec.ts': spec });
  assert.equal(result.status, 0, result.output);
});

test('a skipped frontend test does not cover its scenario', () => {
  for (const name of ['it.skip', 'xit', 'it.todo', 'test.skip']) {
    const spec = `${name}('INV-1.1 an administrator invites', () => {});\n`;
    assertFails(run('trace', { ...documents(), 'frontend/src/a.spec.ts': spec }), /INV-1\.1.*no test/);
  }
});

test('a test inside a skipped describe does not cover its scenario', () => {
  for (const name of ['describe.skip', 'xdescribe']) {
    const spec = `${name}('invitations', () => {\n  it('INV-1.1 invites', () => {});\n});\n`;
    assertFails(run('trace', { ...documents(), 'frontend/src/a.spec.ts': spec }), /INV-1\.1.*no test/);
  }
});

test('a method called test is not a test', () => {
  const spec = `it('checks a pattern', () => {\n  pattern.test('INV-1.1 invites');\n});\n`;
  assertFails(run('trace', { ...documents(), 'frontend/src/a.spec.ts': spec }), /INV-1\.1.*no test/);
});

test('a disabled backend test does not cover its scenario', () => {
  const method = `class T {
    @Disabled
    @Test
    @DisplayName("INV-1.1 invites")
    void invites() {}
}
`;
  assertFails(run('trace', { ...documents(), 'backend/src/test/java/T.java': method }), /INV-1\.1.*no test/);
  const disabledClass = `@Disabled
class T {
    @Test
    @DisplayName("INV-1.1 invites")
    void invites() {}
}
`;
  assertFails(run('trace', { ...documents(), 'backend/src/test/java/T.java': disabledClass }), /INV-1\.1.*no test/);
});

test('a disabled test does not hide the next test', () => {
  const java = `class T {
    @Disabled
    @Test
    void other() {}

    @Test
    @DisplayName("INV-1.1 invites")
    void invites() {}
}
`;
  const result = run('trace', { ...documents(), 'backend/src/test/java/T.java': java });
  assert.equal(result.status, 0, result.output);
});

test('a commented-out test does not cover its scenario', () => {
  const spec = `// it('INV-1.1 invites', () => {});\n/*\nit('INV-1.1 invites', () => {});\n*/\n`;
  assertFails(run('trace', { ...documents(), 'frontend/src/a.spec.ts': spec }), /INV-1\.1.*no test/);
  const java = `class T {
    // @DisplayName("INV-1.1 invites")
    @Test
    void invites() {}
}
`;
  assertFails(run('trace', { ...documents(), 'backend/src/test/java/T.java': java }), /INV-1\.1.*no test/);
});

test('a scenario id on a test class does not cover the scenario', () => {
  const java = `@DisplayName("INV-1.1 invites")
class T {
    @Test
    void invites() {}
}
`;
  assertFails(run('trace', { ...documents(), 'backend/src/test/java/T.java': java }), /T\.java:\d+.*INV-1\.1.*class/);
});

test('@Disabled counts across a comment or a blank line', () => {
  const javaWithComment = `class T {
    @Disabled
    // reason
    @Test
    @DisplayName("INV-1.1 invites")
    void invites() {}
}
`;
  assertFails(run('trace', { ...documents(), 'backend/src/test/java/T.java': javaWithComment }), /INV-1\.1.*no test/);
  const javaWithBlank = `class T {
    @Disabled
    @Test

    @DisplayName("INV-1.1 invites")
    void invites() {}
}
`;
  assertFails(run('trace', { ...documents(), 'backend/src/test/java/T.java': javaWithBlank }), /INV-1\.1.*no test/);
});

test('@DisplayName on a method that is not a test does not cover its scenario', () => {
  const java = `class T {
    @DisplayName("INV-1.1 invites")
    void helper() {}
}
`;
  assertFails(run('trace', { ...documents(), 'backend/src/test/java/T.java': java }), /INV-1\.1.*no test/);
  const inline = 'class T {\n    @DisplayName("INV-1.1 invites") void helper() {}\n}\n';
  assertFails(run('trace', { ...documents(), 'backend/src/test/java/T.java': inline }), /INV-1\.1.*no test/);
});

test('trace counts every JUnit test annotation', () => {
  for (const annotation of ['@Test', '@ParameterizedTest', '@RepeatedTest(2)', '@TestTemplate']) {
    const java = `class T {
    ${annotation}
    @DisplayName("INV-1.1 invites")
    void invites() {}
}
`;
    const result = run('trace', { ...documents(), 'backend/src/test/java/T.java': java });
    assert.equal(result.status, 0, `${annotation}: ${result.output}`);
  }
  const factory = `class T {
    @TestFactory
    @DisplayName("INV-1.1 invites")
    List<DynamicTest> invites() {
        return List.of();
    }
}
`;
  const result = run('trace', { ...documents(), 'backend/src/test/java/T.java': factory });
  assert.equal(result.status, 0, result.output);
});

test('trace counts annotations on one line with the method and a multi-line name', () => {
  const inline = 'class T {\n    @Test @DisplayName("INV-1.1 invites") void invites() {}\n}\n';
  let result = run('trace', { ...documents(), 'backend/src/test/java/T.java': inline });
  assert.equal(result.status, 0, result.output);
  const multiline = `class T {
    @Test
    @DisplayName(
        "INV-1.1 invites")
    void invites() {}
}
`;
  result = run('trace', { ...documents(), 'backend/src/test/java/T.java': multiline });
  assert.equal(result.status, 0, result.output);
});

test('annotations of a field do not reach the next test', () => {
  const java = `class T {
    @Disabled
    private String field;

    @Test
    @DisplayName("INV-1.1 invites")
    void invites() {}
}
`;
  const result = run('trace', { ...documents(), 'backend/src/test/java/T.java': java });
  assert.equal(result.status, 0, result.output);
});

test('a disabled nested class ends with its braces', () => {
  const after = `class T {
    @Nested
    @Disabled
    class Off {
        @Test
        void other() {
            String json = "{";
        }
    }

    @Nested
    class On {
        @Test
        @DisplayName("INV-1.1 invites")
        void invites() {}
    }
}
`;
  const result = run('trace', { ...documents(), 'backend/src/test/java/T.java': after });
  assert.equal(result.status, 0, result.output);
  const inside = `class T {
    @Nested
    @Disabled
    class Off {
        @Test
        @DisplayName("INV-1.1 invites")
        void invites() {}
    }

    @Test
    void other() {}
}
`;
  assertFails(run('trace', { ...documents(), 'backend/src/test/java/T.java': inside }), /INV-1\.1.*no test/);
});

test('trace counts it.each with a generic and with nested calls in the table', () => {
  const generic = `it.each<[Partial<HistoryEntry>, string]>([\n  [{ a: 1 }, 'x'],\n])('INV-1.1 invites %s', () => {});\n`;
  let result = run('trace', { ...documents(), 'frontend/src/a.spec.ts': generic });
  assert.equal(result.status, 0, result.output);
  const nested = `it.each([[f(g(h(1))), ')']])('INV-1.1 invites %s', () => {});\n`;
  result = run('trace', { ...documents(), 'frontend/src/a.spec.ts': nested });
  assert.equal(result.status, 0, result.output);
});

test('trace counts conditional Vitest calls', () => {
  for (const name of ['it.skipIf(c)', 'it.runIf(f(c))', 'it.for([1])']) {
    const spec = `${name}('INV-1.1 invites', () => {});\n`;
    const result = run('trace', { ...documents(), 'frontend/src/a.spec.ts': spec });
    assert.equal(result.status, 0, `${name}: ${result.output}`);
  }
});

test('an id with too many parts is not read as a shorter id', () => {
  assertFails(run('validate', documents(capability(REQUIREMENT, '## Out of scope\n\nSee (INV-1.1.5).\n'))), /INV-1\.1\.5.*does not exist/);
  const spec = `${COVERING_SPEC}it('INV-1.1.5 extra', () => {});\n`;
  assertFails(run('trace', { ...documents(), 'frontend/src/a.spec.ts': spec }), /INV-1\.1\.5.*does not exist/);
});

test('an index link may start with ./', () => {
  const index = INDEX.replace('(invitations.md)', '(./invitations.md)');
  const result = run('validate', documents(capability(), index));
  assert.equal(result.status, 0, result.output);
});

test('--root needs an existing directory', () => {
  const noValue = spawnSync(process.execPath, [TOOL, 'validate', '--root'], { encoding: 'utf8' });
  assert.equal(noValue.status, 2, noValue.stderr);
  assert.doesNotMatch(noValue.stderr, /\n\s+at /);
  const missing = spawnSync(process.execPath, [TOOL, 'validate', '--root', path.join(tmpdir(), 'requirements-missing-root')], { encoding: 'utf8' });
  assert.equal(missing.status, 2, missing.stdout);
});

test('files that are not tests do not count', () => {
  const files = {
    ...documents(),
    'frontend/src/app/invitations.ts': COVERING_SPEC,
    'backend/src/main/java/T.java': '@DisplayName("INV-1.1 invites")\nvoid invites() {}\n',
  };
  assertFails(run('trace', files), /INV-1\.1.*no test/);
});

test('trace fails on invalid requirements', () => {
  const content = capability(REQUIREMENT.replace('SHALL', 'should'));
  assertFails(run('trace', { ...documents(content), 'frontend/src/a.spec.ts': COVERING_SPEC }), /SHALL or MUST/);
});
