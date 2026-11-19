// PreToolUse hook of `ui-designer`: it writes mockups under .screens/ and nothing else, so a round of design never
// touches the plan, the requirements or the code.
import path from 'node:path';
import { deny, readInput, repoRoot, toPosix } from './lib.mjs';

const input = readInput();
const filePath = input.tool_input?.file_path;
if (!filePath) process.exit(0);

const root = repoRoot(filePath) ?? process.env.CLAUDE_PROJECT_DIR ?? process.cwd();
const rel = toPosix(path.relative(root, path.resolve(filePath)));
if (!rel.startsWith('.screens/')) {
  deny(`${rel}: ui-designer writes only under .screens/<change>/mockups/. Put anything else in your report.`);
}
