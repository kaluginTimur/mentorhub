import { availableParallelism } from 'node:os';
import { defineConfig } from 'vitest/config';

// Vitest starts one worker per core by default. Each worker boots the Angular
// test environment (hundreds of MB), so on a many-core machine with little RAM
// the workers starve each other and tests with a real router time out.
export default defineConfig({
  test: {
    maxWorkers: Math.min(4, availableParallelism()),
  },
});
