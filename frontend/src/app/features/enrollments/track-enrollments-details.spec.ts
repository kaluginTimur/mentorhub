import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { listEnrollmentCandidates } from '../../core/api/fn/enrollments/list-enrollment-candidates';
import { listMyEnrollments } from '../../core/api/fn/enrollments/list-my-enrollments';
import { listTrackEnrollments } from '../../core/api/fn/enrollments/list-track-enrollments';
import { getTrack } from '../../core/api/fn/tracks/get-track';
import { getTrackHistory } from '../../core/api/fn/tracks/get-track-history';
import { listTrackCoordinatorCandidates } from '../../core/api/fn/tracks/list-track-coordinator-candidates';
import { signal } from '@angular/core';
import { MeUser } from '../../core/api/models/me-user';
import { Permission } from '../../core/api/models/permission';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { problemError } from '../../core/testing/problem-error';
import { track, trackAdmin } from '../tracks/test-data';
import { TrackPage } from '../tracks/track-page';
import { MyEnrollmentPage } from './my-enrollment-page';
import {
  TRACK_ID,
  contact,
  enrollmentPage,
  enrollmentsAdmin,
  myEnrollment,
  trackEnrollment,
} from './test-data';
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

const buttonByText = (root: ParentNode, text: string) =>
  [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === text);

describe('the enrollments of a track, as a paged list', () => {
  const api = createApiStub();
  const me = signal<MeUser | null>(enrollmentsAdmin());
  const currentUser = {
    user: me,
    load: vi.fn(),
    hasPermission: (p: Permission) => me()?.permissions.includes(p) ?? false,
  };

  beforeEach(() => {
    api.reset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '', component: Host }]),
        provideApiStub(api),
        { provide: CurrentUserService, useValue: currentUser },
      ],
    });
  });

  async function open(url = '/') {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, Host);
    await harness.fixture.whenStable();
    harness.detectChanges();
    const element = harness.routeNativeElement as HTMLElement;
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
      await harness.fixture.whenStable();
    };
    return { element, settle };
  }

  it('offers Retry when the list fails to load, and loads it again', async () => {
    api.rejectOnce(
      listTrackEnrollments,
      problemError({ type: 'about:blank', status: 500, title: 'Error' }),
    );
    api.resolve(listTrackEnrollments, enrollmentPage([trackEnrollment()]));
    const { element, settle } = await open();
    await vi.waitFor(() => expect(buttonByText(element, 'Retry')).toBeDefined());
    expect(element.textContent).not.toContain('No students enrolled yet');

    buttonByText(element, 'Retry')?.click();
    await settle();

    await vi.waitFor(() => expect(element.textContent).toContain('Sam Student'));
    expect(api.callsFor(listTrackEnrollments)).toHaveLength(2);
  });

  it('moves to the next page, asks for it and keeps it in the address', async () => {
    api.resolve(listTrackEnrollments, enrollmentPage([trackEnrollment()], { totalItems: 60 }));
    const { element, settle } = await open();
    await vi.waitFor(() => expect(element.textContent).toContain('Sam Student'));

    (element.querySelector('button[aria-label="Next page"]') as HTMLButtonElement).click();
    await settle();

    const router = TestBed.inject(Router);
    expect(router.parseUrl(router.url).queryParams['enrollmentsPage']).toBe('1');
    await vi.waitFor(() =>
      expect(api.callsFor(listTrackEnrollments)).toContainEqual(
        expect.objectContaining({ id: TRACK_ID, page: 1 }),
      ),
    );
  });

  // PLT-9.2 applies to every paged list: a page past the last says so and does not claim the list is empty.
  it('says there are no items on a page past the last one, and lets the user move back', async () => {
    api.resolve(listTrackEnrollments, enrollmentPage([], { page: 5, totalItems: 60 }));
    const { element } = await open('/?enrollmentsPage=5');

    await vi.waitFor(() => expect(element.textContent).toMatch(/on this page/i));
    expect(element.textContent).not.toContain('No students enrolled yet');
    expect(element.querySelector('button[aria-label="Previous page"]')).not.toBeNull();
  });

  it('says there are no items on a page past the last one also for a small total, and lets the user move back', async () => {
    api.resolve(listTrackEnrollments, enrollmentPage([], { page: 2, totalItems: 3 }));
    const { element, settle } = await open('/?enrollmentsPage=2');

    await vi.waitFor(() => expect(element.textContent).toMatch(/on this page/i));
    expect(element.textContent).not.toContain('No students enrolled yet');
    const previous = element.querySelector<HTMLButtonElement>('button[aria-label="Previous page"]');
    expect(previous, 'a way back').not.toBeNull();

    previous?.click();
    await settle();

    const router = TestBed.inject(Router);
    expect(Number(router.parseUrl(router.url).queryParams['enrollmentsPage'])).toBeLessThan(2);
  });

  it('says No students enrolled yet, with no paginator, when the track has no enrollment', async () => {
    api.resolve(listTrackEnrollments, enrollmentPage([]));
    const { element } = await open();

    await vi.waitFor(() => expect(element.textContent).toContain('No students enrolled yet'));
    expect(element.textContent).not.toMatch(/on this page/i);
    expect(element.querySelector('app-paginator')).toBeNull();
  });
});

describe('the track page without ENROLLMENTS_READ', () => {
  const api = createApiStub();
  const me = signal<MeUser | null>(trackAdmin());
  const currentUser = {
    user: me,
    load: vi.fn(),
    hasPermission: (p: Permission) => me()?.permissions.includes(p) ?? false,
  };

  beforeEach(() => {
    api.reset();
    me.set(trackAdmin({ permissions: ['TRACKS_READ', 'TRACKS_MANAGE'] }));
    api.resolve(getTrack, track());
    api.resolve(getTrackHistory, { items: [], page: 0, size: 25, totalItems: 0 });
    api.resolve(listTrackCoordinatorCandidates, { items: [] });
    api.resolve(listTrackEnrollments, enrollmentPage([trackEnrollment()]));
    api.resolve(listEnrollmentCandidates, { items: [], page: 0, size: 25, totalItems: 0 });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'tracks/:id', component: TrackPage }]),
        provideApiStub(api),
        { provide: CurrentUserService, useValue: currentUser },
      ],
    });
  });

  it('shows no enrollments and does not ask for them', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/tracks/${TRACK_ID}`);
    await harness.fixture.whenStable();
    harness.detectChanges();
    const element = harness.routeNativeElement as HTMLElement;

    await vi.waitFor(() => expect(element.textContent).toContain('Quality Assurance'));
    expect(element.textContent).not.toContain('Sam Student');
    expect(buttonByText(element, 'Enroll student')).toBeUndefined();
    expect(api.callsFor(listTrackEnrollments)).toHaveLength(0);
  });
});

describe('My enrollment without a phone', () => {
  const api = createApiStub();

  beforeEach(() => {
    api.reset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'my-enrollment', component: MyEnrollmentPage }]),
        provideApiStub(api),
        {
          provide: CurrentUserService,
          useValue: { user: signal(null), hasPermission: () => false },
        },
      ],
    });
  });

  it('shows the coordinator without a phone and never the word null', async () => {
    api.resolve(listMyEnrollments, {
      items: [myEnrollment({ coordinators: [contact({ phone: null })] })],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/my-enrollment');
    await harness.fixture.whenStable();
    harness.detectChanges();
    const element = harness.routeNativeElement as HTMLElement;

    await vi.waitFor(() => expect(element.textContent).toContain('Clara Coordinator'));
    expect(element.textContent).toContain('clara@example.com');
    expect(element.textContent).not.toContain('null');
    expect(element.textContent).not.toContain('undefined');
  });
});
