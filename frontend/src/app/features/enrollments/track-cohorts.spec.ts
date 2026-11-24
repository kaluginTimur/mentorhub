import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { createCohort } from '../../core/api/fn/cohorts/create-cohort';
import { deleteCohort } from '../../core/api/fn/cohorts/delete-cohort';
import { listNewCohortCandidates } from '../../core/api/fn/cohorts/list-new-cohort-candidates';
import { listTrackCohorts } from '../../core/api/fn/cohorts/list-track-cohorts';
import { enrollStudent } from '../../core/api/fn/enrollments/enroll-student';
import { listEnrollmentCandidates } from '../../core/api/fn/enrollments/list-enrollment-candidates';
import { listTrackEnrollments } from '../../core/api/fn/enrollments/list-track-enrollments';
import { getTrack } from '../../core/api/fn/tracks/get-track';
import { getTrackHistory } from '../../core/api/fn/tracks/get-track-history';
import { listTrackCoordinatorCandidates } from '../../core/api/fn/tracks/list-track-coordinator-candidates';
import { Cohort } from '../../core/api/models/cohort';
import { CohortCandidate } from '../../core/api/models/cohort-candidate';
import { MeUser } from '../../core/api/models/me-user';
import { Permission } from '../../core/api/models/permission';
import { Track } from '../../core/api/models/track';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { problemError } from '../../core/testing/problem-error';
import { track } from '../tracks/test-data';
import { TrackPage } from '../tracks/track-page';
import {
  TRACK_ID,
  enrolledStudent,
  enrollmentCandidate,
  enrollmentPage,
  enrollmentsAdmin,
  trackEnrollment,
} from './test-data';

// The cohorts on the track page (COH-8) and enrolling into a cohort (ENR-7). The texts, roles and data-testids are
// those of "What the tests rely on" in the plan.

const problem = (status: number, type: string) => problemError({ type, status, title: 'Error' });
const byId = (id: string) => document.querySelector<HTMLElement>(`[data-testid="${id}"]`);
const allById = (id: string) => [
  ...document.querySelectorAll<HTMLElement>(`[data-testid="${id}"]`),
];
const snack = () => document.querySelector('app-toast-host')?.textContent?.trim() ?? '';

function setValue(field: HTMLInputElement | HTMLSelectElement | null, value: string) {
  expect(field, 'the field').not.toBeNull();
  if (field) {
    field.value = value;
    field.dispatchEvent(new Event('input', { bubbles: true }));
    field.dispatchEvent(new Event('change', { bubbles: true }));
  }
}

const SPRING = '51111111-1111-4111-8111-111111111111';
const AUTUMN = '52222222-2222-4222-8222-222222222222';
const EMPTY = '53333333-3333-4333-8333-333333333333';

const cohort = (overrides: Partial<Cohort> = {}): Cohort => ({
  id: SPRING,
  name: 'Spring 2026',
  startDate: '2026-03-01',
  enrollmentCount: 1,
  ...overrides,
});

const candidate = (
  id: string,
  name: string,
  current: { id: string; name: string } | null,
): CohortCandidate => ({
  id,
  student: enrolledStudent({ id: `s-${id}`, name, email: `${id}@example.com` }),
  cohort: current,
});

describe('the cohorts on the track page', () => {
  const api = createApiStub();
  const me = signal<MeUser | null>(enrollmentsAdmin());
  const currentUser = {
    user: me,
    load: vi.fn(),
    hasPermission: (p: Permission) => me()?.permissions.includes(p) ?? false,
  };

  // The first load shows the track as the page opened it; every later one, the track as it is now.
  const trackChangedMeanwhile = (now: Track) => {
    let loads = 0;
    api.handle(getTrack, () => (loads++ === 0 ? track() : now));
  };

  beforeEach(() => {
    api.reset();
    me.set(enrollmentsAdmin());
    api.resolve(getTrack, track());
    api.resolve(getTrackHistory, { items: [], page: 0, size: 25, totalItems: 0 });
    api.resolve(listTrackCoordinatorCandidates, { items: [] });
    api.resolve(listTrackEnrollments, enrollmentPage([trackEnrollment()]));
    api.resolve(listTrackCohorts, {
      items: [cohort(), cohort({ id: EMPTY, name: 'Empty', enrollmentCount: 0 })],
    });
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

  async function openCreateDialog(settle: () => Promise<void>) {
    await vi.waitFor(() => expect(byId('create-cohort')).not.toBeNull());
    byId('create-cohort')?.click();
    await settle();
    await vi.waitFor(() => expect(byId('create-cohort-dialog')).not.toBeNull());
  }

  async function choose(enrollmentId: string, settle: () => Promise<void>) {
    await vi.waitFor(() =>
      expect(
        allById('candidate-checkbox').find((c) => c.dataset['enrollmentId'] === enrollmentId),
      ).toBeDefined(),
    );
    allById('candidate-checkbox')
      .find((c) => c.dataset['enrollmentId'] === enrollmentId)
      ?.click();
    await settle();
  }

  const candidates = () => {
    api.resolve(listNewCohortCandidates, {
      items: [
        candidate('e1', 'Anna Alpha', { id: SPRING, name: 'Spring 2026' }),
        candidate('e2', 'Boris Bravo', { id: AUTUMN, name: 'Autumn 2025' }),
        candidate('e3', 'Cleo Charlie', null),
      ],
      page: 0,
      size: 25,
      totalItems: 3,
    });
  };

  async function fillAndSave(settle: () => Promise<void>) {
    await openCreateDialog(settle);
    setValue(byId('cohort-name') as HTMLInputElement, 'Winter 2027');
    setValue(byId('cohort-start-date') as HTMLInputElement, '2027-01-11');
    await choose('e1', settle);
    await choose('e2', settle);
    await choose('e3', settle);
    byId('create-cohort-submit')?.click();
    await settle();
  }

  it('COH-8.1 names the students who leave their cohorts and sends nothing until the user confirms', async () => {
    candidates();
    api.resolve(createCohort, cohort({ id: 'new', name: 'Winter 2027', enrollmentCount: 3 }));
    const { settle } = await open();

    await fillAndSave(settle);

    await vi.waitFor(() => expect(byId('move-confirm-dialog')).not.toBeNull());
    const text = byId('move-confirm-dialog')?.textContent ?? '';
    expect(text).toContain('Anna Alpha leaves Spring 2026');
    expect(text).toContain('Boris Bravo leaves Autumn 2025');
    expect(text).not.toContain('Cleo Charlie leaves');
    expect(api.callsFor(createCohort), 'nothing is sent yet').toHaveLength(0);

    byId('move-confirm')?.click();
    await settle();

    await vi.waitFor(() => expect(api.callsFor(createCohort)).toHaveLength(1));
    expect(api.callsFor(createCohort)[0].body.enrollmentIds).toEqual(
      expect.arrayContaining(['e1', 'e2', 'e3']),
    );
  });

  it('COH-8.1 sends nothing when the user declines the confirmation', async () => {
    candidates();
    api.resolve(createCohort, cohort({ id: 'new', name: 'Winter 2027', enrollmentCount: 3 }));
    const { settle } = await open();

    await fillAndSave(settle);
    await vi.waitFor(() => expect(byId('move-confirm-dialog')).not.toBeNull());
    const cancel = [...(byId('move-confirm-dialog')?.querySelectorAll('button') ?? [])].find(
      (b) => b.textContent?.trim() === 'Cancel',
    );
    cancel?.click();
    await settle();

    expect(api.callsFor(createCohort)).toHaveLength(0);
  });

  it('COH-8.2 explains enrollment-not-eligible on creating a cohort and shows the cohorts and the enrollments as they are now', async () => {
    trackChangedMeanwhile(track({ name: 'Quality Engineering' }));
    api.reject(createCohort, problem(409, 'urn:mentorhub:problem:enrollment-not-eligible'));
    const { element, settle } = await open();
    await vi.waitFor(() => expect(byId('create-cohort')).not.toBeNull());
    const cohortLoads = api.callsFor(listTrackCohorts).length;
    const enrollmentLoads = api.callsFor(listTrackEnrollments).length;

    await openCreateDialog(settle);
    setValue(byId('cohort-name') as HTMLInputElement, 'Winter 2027');
    setValue(byId('cohort-start-date') as HTMLInputElement, '2027-01-11');
    byId('create-cohort-submit')?.click();
    await settle();

    expect(api.callsFor(createCohort)).toHaveLength(1);
    await vi.waitFor(() => expect(snack()).toMatch(/\S/));
    await vi.waitFor(() => expect(element.textContent).toContain('Quality Engineering'));
    await vi.waitFor(() => {
      expect(api.callsFor(listTrackCohorts).length).toBeGreaterThan(cohortLoads);
      expect(api.callsFor(listTrackEnrollments).length).toBeGreaterThan(enrollmentLoads);
    });
  });

  it('COH-8.2 explains cohort-not-empty on deleting a cohort and shows the cohorts and the enrollments as they are now', async () => {
    trackChangedMeanwhile(track({ name: 'Quality Engineering' }));
    api.reject(deleteCohort, problem(409, 'urn:mentorhub:problem:cohort-not-empty'));
    const { element, settle } = await open();
    await vi.waitFor(() => expect(allById('cohort-item').length).toBeGreaterThan(0));
    const cohortLoads = api.callsFor(listTrackCohorts).length;
    const enrollmentLoads = api.callsFor(listTrackEnrollments).length;

    allById('cohort-item')
      .find((i) => i.dataset['cohortId'] === EMPTY)
      ?.click();
    await settle();
    await vi.waitFor(() => expect(byId('cohort-actions-menu')).not.toBeNull());
    byId('cohort-actions-menu')?.click();
    await settle();
    await vi.waitFor(() => expect(byId('cohort-menu-delete')).not.toBeNull());
    byId('cohort-menu-delete')?.click();
    await settle();
    await vi.waitFor(() => expect(byId('delete-cohort-dialog')).not.toBeNull());
    byId('delete-cohort-confirm')?.click();
    await settle();

    expect(api.callsFor(deleteCohort)).toHaveLength(1);
    await vi.waitFor(() => expect(snack()).toMatch(/\S/));
    await vi.waitFor(() => expect(element.textContent).toContain('Quality Engineering'));
    await vi.waitFor(() => {
      expect(api.callsFor(listTrackCohorts).length).toBeGreaterThan(cohortLoads);
      expect(api.callsFor(listTrackEnrollments).length).toBeGreaterThan(enrollmentLoads);
    });
  });

  it('ENR-7.1 explains cohort-not-eligible on enrolling into a cohort and shows the track, its cohorts and its enrollments as they are now', async () => {
    trackChangedMeanwhile(track({ name: 'Quality Engineering' }));
    api.resolve(listEnrollmentCandidates, {
      items: [enrollmentCandidate()],
      page: 0,
      size: 25,
      totalItems: 1,
    });
    api.reject(enrollStudent, problem(409, 'urn:mentorhub:problem:cohort-not-eligible'));
    const { element, settle } = await open();
    await vi.waitFor(() => expect(byId('enroll-student')).not.toBeNull());
    const cohortLoads = api.callsFor(listTrackCohorts).length;

    byId('enroll-student')?.click();
    await settle();
    await vi.waitFor(() => expect(byId('enroll-cohort')).not.toBeNull());
    const select = byId('enroll-cohort') as HTMLSelectElement;
    const option = [...select.options].find((o) => o.textContent?.includes('Spring 2026'));
    expect(option, 'an option for Spring 2026').toBeDefined();
    setValue(select, option?.value ?? '');
    await settle();
    const student = [...document.querySelectorAll<HTMLInputElement>('input')].find(
      (i) => i.id === 'enroll-student-search',
    );
    student?.click();
    await settle();
    await vi.waitFor(() => {
      const o = [...document.querySelectorAll<HTMLElement>('[role=option]')].find((x) =>
        x.textContent?.includes('Olga Candidate'),
      );
      expect(o, 'an option for Olga Candidate').toBeDefined();
      o?.click();
    });
    await settle();
    [...document.querySelectorAll('button')]
      .find((b) => b.textContent?.trim() === 'Enroll')
      ?.click();
    await settle();

    await vi.waitFor(() => expect(api.callsFor(enrollStudent)).toHaveLength(1));
    expect(api.callsFor(enrollStudent)[0].body.cohortId).toBeDefined();
    await vi.waitFor(() => expect(snack()).toMatch(/\S/));
    await vi.waitFor(() => expect(element.textContent).toContain('Quality Engineering'));
    await vi.waitFor(() =>
      expect(api.callsFor(listTrackCohorts).length).toBeGreaterThan(cohortLoads),
    );
  });
});
