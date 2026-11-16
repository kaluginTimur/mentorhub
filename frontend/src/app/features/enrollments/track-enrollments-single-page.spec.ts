import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { listTrackEnrollments } from '../../core/api/fn/enrollments/list-track-enrollments';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { TRACK_ID, enrollmentPage, trackEnrollment } from './test-data';
import { TrackEnrollments } from './track-enrollments';

@Component({
  imports: [TrackEnrollments],
  template: `<app-track-enrollments
    [trackId]="trackId"
    [canEnroll]="false"
    trackStatus="ACTIVE"
  />`,
})
class Host {
  readonly trackId = TRACK_ID;
}

describe('the enrollments of a track that fit on one page', () => {
  const api = createApiStub();

  beforeEach(() => {
    api.reset();
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '', component: Host }]), provideApiStub(api)],
    });
  });

  it('end with the last row: no paginator, so no separator line under the list', async () => {
    api.resolve(listTrackEnrollments, enrollmentPage([trackEnrollment()], { totalItems: 1 }));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/', Host);
    const element = harness.routeNativeElement as HTMLElement;
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(element.textContent).toContain('Sam Student');
    });
    expect(element.querySelector('app-paginator')).toBeNull();
  });
});
