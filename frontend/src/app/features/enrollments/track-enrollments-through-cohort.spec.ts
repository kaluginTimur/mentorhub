import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { listTrackCohorts } from '../../core/api/fn/cohorts/list-track-cohorts';
import { listTrackEnrollments } from '../../core/api/fn/enrollments/list-track-enrollments';
import { removeEnrollmentMentor } from '../../core/api/fn/enrollments/remove-enrollment-mentor';
import { getTrack } from '../../core/api/fn/tracks/get-track';
import { getTrackHistory } from '../../core/api/fn/tracks/get-track-history';
import { listTrackCoordinatorCandidates } from '../../core/api/fn/tracks/list-track-coordinator-candidates';
import { removeTrackMentor } from '../../core/api/fn/tracks/remove-track-mentor';
import { Cohort } from '../../core/api/models/cohort';
import { MeUser } from '../../core/api/models/me-user';
import { Permission } from '../../core/api/models/permission';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { problemError } from '../../core/testing/problem-error';
import { track, trackMentor } from '../tracks/test-data';
import { TrackPage } from '../tracks/track-page';
import {
  MENTOR_ID,
  TRACK_ID,
  cohortMentor,
  enrollmentMentor,
  enrollmentPage,
  enrollmentsAdmin,
  trackEnrollment,
} from './test-data';

// The marks of the mentors who lead an enrollment through its cohort on the track page (ENR-7), and the refusal of
// removing a mentor of a track who leads cohorts (ENR-7.1).

const SPRING = '51111111-1111-4111-8111-111111111111';
const problem = (status: number, type: string) => problemError({ type, status, title: 'Error' });
const snack = () => document.querySelector('app-toast-host')?.textContent?.trim() ?? '';
const overlay = () =>
  (document.querySelector('.cdk-overlay-container') as HTMLElement | null) ??
  document.createElement('div');
const nameOf = (e: Element) => (e.getAttribute('aria-label') ?? e.textContent ?? '').trim();
const buttonNamed = (root: ParentNode, wanted: string | RegExp) =>
  [...root.querySelectorAll('button')].find((b) =>
    typeof wanted === 'string' ? nameOf(b) === wanted : wanted.test(nameOf(b)),
  );

const cohort = (overrides: Partial<Cohort> = {}): Cohort => ({
  id: SPRING,
  name: 'Spring 2026',
  startDate: '2026-03-01',
  enrollmentCount: 1,
  mentors: [cohortMentor()],
  ...overrides,
});
const inCohort = { id: SPRING, name: 'Spring 2026' };

describe('the mentors of an enrollment through its cohort', () => {
  const api = createApiStub();
  const me = signal<MeUser | null>(enrollmentsAdmin());
  const currentUser = {
    user: me,
    load: vi.fn(),
    hasPermission: (p: Permission) => me()?.permissions.includes(p) ?? false,
  };

  beforeEach(() => {
    api.reset();
    me.set(enrollmentsAdmin());
    api.resolve(getTrack, track({ mentors: [trackMentor()] }));
    api.resolve(getTrackHistory, { items: [], page: 0, size: 25, totalItems: 0 });
    api.resolve(listTrackCoordinatorCandidates, { items: [] });
    api.resolve(listTrackCohorts, { items: [cohort()] });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'tracks/:id', component: TrackPage }]),
        provideApiStub(api),
        { provide: CurrentUserService, useValue: currentUser },
      ],
    });
  });

  afterEach(() => {
    document.querySelector('.cdk-overlay-container')?.replaceChildren();
  });

  async function open() {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/tracks/${TRACK_ID}`);
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

  it('marks a mentor who leads only through the cohort and offers no removal for them', async () => {
    api.resolve(
      listTrackEnrollments,
      enrollmentPage([
        trackEnrollment({
          cohort: inCohort,
          mentors: [enrollmentMentor({ throughCohort: true })],
        }),
      ]),
    );
    const { element, settle } = await open();

    await vi.waitFor(() =>
      expect(buttonNamed(element, 'Mia Mentor, through cohort')).toBeDefined(),
    );
    buttonNamed(element, 'Mia Mentor, through cohort')?.click();
    await settle();

    expect(overlay().textContent).toContain('Through cohort');
    expect(buttonNamed(overlay(), /^Remove\b/)).toBeUndefined();
  });

  it('offers the removal of a mentor of the enrollment itself, without the mark', async () => {
    api.resolve(
      listTrackEnrollments,
      enrollmentPage([trackEnrollment({ cohort: inCohort, mentors: [enrollmentMentor()] })]),
    );
    const { element, settle } = await open();

    await vi.waitFor(() => expect(buttonNamed(element, 'Mia Mentor')).toBeDefined());
    buttonNamed(element, 'Mia Mentor')?.click();
    await settle();

    expect(buttonNamed(overlay(), /^Remove\b/)).toBeDefined();
    expect(overlay().textContent).not.toContain('Through cohort');
  });

  it('says in the confirmation that the mentor still leads the student through the cohort', async () => {
    api.resolve(
      listTrackEnrollments,
      enrollmentPage([trackEnrollment({ cohort: inCohort, mentors: [enrollmentMentor()] })]),
    );
    api.resolve(removeEnrollmentMentor, trackEnrollment());
    const { element, settle } = await open();
    await vi.waitFor(() => expect(buttonNamed(element, 'Mia Mentor')).toBeDefined());
    buttonNamed(element, 'Mia Mentor')?.click();
    await settle();
    buttonNamed(overlay(), /^Remove\b/)?.click();
    await settle();

    expect(overlay().textContent).toContain(
      'They stop being a mentor of this student, but still lead Sam Student through Spring 2026. They can be assigned again later.',
    );
    expect(api.callsFor(removeEnrollmentMentor)).toHaveLength(0);
  });

  it('does not say it when the mentor does not lead the student through the cohort', async () => {
    api.resolve(listTrackCohorts, { items: [cohort({ mentors: [] })] });
    api.resolve(
      listTrackEnrollments,
      enrollmentPage([trackEnrollment({ cohort: inCohort, mentors: [enrollmentMentor()] })]),
    );
    const { element, settle } = await open();
    await vi.waitFor(() => expect(buttonNamed(element, 'Mia Mentor')).toBeDefined());
    buttonNamed(element, 'Mia Mentor')?.click();
    await settle();
    buttonNamed(overlay(), /^Remove\b/)?.click();
    await settle();

    expect(overlay().textContent).toContain('Remove');
    expect(overlay().textContent).not.toContain('still lead');
  });

  it('ENR-7.1 explains mentor-leads-cohorts on removing a mentor of the track and shows the track, its cohorts and its enrollments as they are now', async () => {
    api.resolve(listTrackEnrollments, enrollmentPage([trackEnrollment()]));
    let loads = 0;
    api.handle(getTrack, () =>
      loads++ === 0
        ? track({ mentors: [trackMentor()] })
        : track({ name: 'Quality Engineering', mentors: [trackMentor()] }),
    );
    api.reject(removeTrackMentor, problem(409, 'urn:mentorhub:problem:mentor-leads-cohorts'));
    const { element, settle } = await open();
    const cohortLoads = api.callsFor(listTrackCohorts).length;
    const enrollmentLoads = api.callsFor(listTrackEnrollments).length;

    element.querySelector<HTMLButtonElement>('button[aria-label="Remove Mia Mentor"]')?.click();
    await settle();
    buttonNamed(overlay(), 'Remove')?.click();
    await settle();

    expect(api.callsFor(removeTrackMentor)).toEqual([{ id: TRACK_ID, userId: MENTOR_ID }]);
    await vi.waitFor(() => expect(snack()).toMatch(/\S/));
    await vi.waitFor(() => expect(element.textContent).toContain('Quality Engineering'));
    await vi.waitFor(() => {
      expect(api.callsFor(listTrackCohorts).length).toBeGreaterThan(cohortLoads);
      expect(api.callsFor(listTrackEnrollments).length).toBeGreaterThan(enrollmentLoads);
    });
  });
});
