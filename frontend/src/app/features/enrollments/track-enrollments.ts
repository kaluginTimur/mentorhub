import { Component, input } from '@angular/core';
import { TrackStatus } from '../../core/api/models/track-status';

/** The enrollments of a track, for a user with ENROLLMENTS_READ (ENR-7). */
@Component({
  selector: 'app-track-enrollments',
  template: '',
})
export class TrackEnrollments {
  readonly trackId = input.required<string>();
  readonly canEnroll = input.required<boolean>();
  readonly trackStatus = input.required<TrackStatus>();
}
