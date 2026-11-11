import { Component, input } from '@angular/core';

export type StatusTone = 'success' | 'info' | 'neutral';

/** A small colored dot before a status text. The text carries the meaning, so the dot is decorative. */
@Component({
  selector: 'app-status-dot',
  template: '',
  host: { 'aria-hidden': 'true', '[attr.data-tone]': 'tone()' },
  styles: `
    :host {
      display: inline-block;
      inline-size: 8px;
      block-size: 8px;
      margin-inline-end: 8px;
      border-radius: 50%;
      background: var(--app-status-neutral);
    }
    :host([data-tone='success']) {
      background: var(--app-status-success);
    }
    :host([data-tone='info']) {
      background: var(--app-status-info);
    }
  `,
})
export class StatusDot {
  readonly tone = input.required<StatusTone>();
}
