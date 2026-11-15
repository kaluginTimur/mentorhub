import { Component, input } from '@angular/core';
import { HlmBadge } from '@spartan-ng/helm/badge';
import { TrackStatus } from '../../core/api/models/track-status';
import { TRACK_STATUS_LABELS } from './labels';

/** Two separate elements, so a status change never leaves the other status's classes behind. */
@Component({
  selector: 'app-track-status-chip',
  imports: [HlmBadge],
  host: { class: 'flex' },
  template: `
    @if (status() === 'ACTIVE') {
      <span
        hlmBadge
        variant="secondary"
        class="border-transparent bg-mh-success-soft text-mh-success-soft-foreground"
      >
        {{ labels.ACTIVE }}
      </span>
    } @else {
      <span hlmBadge variant="outline">{{ labels.ARCHIVED }}</span>
    }
  `,
})
export class TrackStatusChip {
  readonly status = input.required<TrackStatus>();
  protected readonly labels = TRACK_STATUS_LABELS;
}
