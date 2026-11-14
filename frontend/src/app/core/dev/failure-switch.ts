import { Component, computed, inject } from '@angular/core';
import { FAILURE_MODES, FailureMode, FailureModeName } from './failure-mode';

const LABELS: Record<FailureModeName, string> = {
  normal: 'Normal',
  slow: 'Slow',
  'server-error': 'Server error',
  offline: 'Offline',
};

@Component({
  selector: 'app-failure-switch',
  host: {
    class: 'fixed right-2 bottom-2 z-50 flex flex-col items-end gap-1 text-xs',
  },
  template: `
    @if (active()) {
      <div
        class="rounded border border-destructive/50 bg-destructive/10 px-2 py-0.5 font-medium text-destructive shadow-xs"
        role="status"
        data-testid="failure-indicator"
      >
        Failure mode on: {{ label() }}
      </div>
    }
    <select
      aria-label="Simulated API failures"
      data-testid="failure-switch"
      class="rounded border border-border bg-card px-2 py-0.5 text-xs text-foreground shadow-xs"
      [value]="failureMode.mode()"
      (change)="select($event)"
    >
      @for (mode of modes; track mode) {
        <option [value]="mode" [selected]="mode === failureMode.mode()">{{ labels[mode] }}</option>
      }
    </select>
  `,
})
export class FailureSwitch {
  protected readonly failureMode = inject(FailureMode);
  protected readonly modes = FAILURE_MODES;
  protected readonly labels = LABELS;
  protected readonly active = computed(() => this.failureMode.mode() !== 'normal');
  protected readonly label = computed(() => LABELS[this.failureMode.mode()]);

  protected select(event: Event): void {
    this.failureMode.mode.set((event.target as HTMLSelectElement).value as FailureModeName);
  }
}
