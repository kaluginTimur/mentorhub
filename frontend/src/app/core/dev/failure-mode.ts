import { Service, signal } from '@angular/core';

export const FAILURE_MODES = ['normal', 'slow', 'server-error', 'offline'] as const;
export type FailureModeName = (typeof FAILURE_MODES)[number];

/** The simulated failure applied to API requests; in memory only, so a reload turns it off. */
@Service()
export class FailureMode {
  readonly mode = signal<FailureModeName>('normal');
}
