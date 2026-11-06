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
  template: `
    @if (active()) {
      <div class="indicator" role="status" data-testid="failure-indicator">
        Failure mode on: {{ label() }}
      </div>
    }
    <select
      aria-label="Simulated API failures"
      data-testid="failure-switch"
      [value]="failureMode.mode()"
      (change)="select($event)"
    >
      @for (mode of modes; track mode) {
        <option [value]="mode" [selected]="mode === failureMode.mode()">{{ labels[mode] }}</option>
      }
    </select>
  `,
  styles: `
    :host {
      position: fixed;
      right: 8px;
      bottom: 8px;
      z-index: 1000;
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 4px;
      font: var(--mat-sys-label-small);
    }
    select,
    .indicator {
      font: inherit;
      border-radius: var(--mat-sys-corner-small);
      border: 1px solid var(--mat-sys-outline);
      padding: 2px 6px;
      background: var(--mat-sys-surface-container);
      color: var(--mat-sys-on-surface);
    }
    .indicator {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
      border-color: var(--mat-sys-error);
    }
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
