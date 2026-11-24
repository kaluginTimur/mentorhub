import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { addCohortEnrollments } from '../../core/api/fn/cohorts/add-cohort-enrollments';
import { createCohort } from '../../core/api/fn/cohorts/create-cohort';
import { deleteCohort } from '../../core/api/fn/cohorts/delete-cohort';
import { listNewCohortCandidates } from '../../core/api/fn/cohorts/list-new-cohort-candidates';
import { listTrackCohorts } from '../../core/api/fn/cohorts/list-track-cohorts';
import { listCohortCandidates } from '../../core/api/fn/cohorts/list-cohort-candidates';
import { removeCohortEnrollment } from '../../core/api/fn/cohorts/remove-cohort-enrollment';
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

// Details of the cohorts on the track page that no scenario pins: what the URL and the lists send, who sees the
// actions, and the confirmations. Test ids and texts are those of "What the tests rely on" in the plan.

const byId = (id: string) => document.querySelector<HTMLElement>(`[data-testid="${id}"]`);
const allById = (id: string) => [
  ...document.querySelectorAll<HTMLElement>(`[data-testid="${id}"]`),
];

const problem = (status: number, type: string) => problemError({ type, status, title: 'Error' });
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
const EMPTY = '53333333-3333-4333-8333-333333333333';
const IN_SPRING = '71111111-1111-4111-8111-111111111111';

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

describe('details of the cohorts on the track page', () => {
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
    api.resolve(getTrack, track());
    api.resolve(getTrackHistory, { items: [], page: 0, size: 25, totalItems: 0 });
    api.resolve(listTrackCoordinatorCandidates, { items: [] });
    api.resolve(
      listTrackEnrollments,
      enrollmentPage([
        trackEnrollment({ id: IN_SPRING, cohort: { id: SPRING, name: 'Spring 2026' } }),
      ]),
    );
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

  async function open(url = `/tracks/${TRACK_ID}`) {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
    harness.detectChanges();
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
      await harness.fixture.whenStable();
    };
    return { harness, settle };
  }

  const item = (id: string) => allById('cohort-item').find((i) => i.dataset['cohortId'] === id);

  it('keeps the chosen cohort in the URL, narrows the list with it and clears it with All enrollments', async () => {
    const { settle } = await open();
    await vi.waitFor(() => expect(allById('cohort-item')).toHaveLength(2));

    item(SPRING)?.click();
    await settle();

    await vi.waitFor(() => expect(TestBed.inject(Router).url).toContain(`cohort=${SPRING}`));
    await vi.waitFor(() =>
      expect(api.callsFor(listTrackEnrollments).some((c) => c.cohortId === SPRING)).toBe(true),
    );
    expect(item(SPRING)?.getAttribute('aria-current')).toBe('true');

    byId('cohort-all')?.click();
    await settle();

    await vi.waitFor(() => expect(TestBed.inject(Router).url).not.toContain('cohort='));
    expect(byId('cohort-all')?.getAttribute('aria-current')).toBe('true');
  });

  it('treats a cohort in the URL that the track does not have as All enrollments', async () => {
    const { settle } = await open(
      `/tracks/${TRACK_ID}?cohort=99999999-9999-4999-8999-999999999999`,
    );
    await settle();

    await vi.waitFor(() => expect(allById('cohort-item')).toHaveLength(2));
    expect(allById('cohort-item').some((i) => i.getAttribute('aria-current') === 'true')).toBe(
      false,
    );
    expect(byId('cohort-all')?.getAttribute('aria-current')).toBe('true');
  });

  it('shows no cohort list and no cohort actions on a track without cohorts', async () => {
    api.resolve(listTrackCohorts, { items: [] });
    const { settle } = await open();
    await settle();

    await vi.waitFor(() => expect(byId('create-cohort')).not.toBeNull());
    expect(byId('cohort-list')).toBeNull();
    expect(byId('cohort-select')).toBeNull();
    expect(byId('cohort-add-students')).toBeNull();
  });

  it('offers a read-only user the cohorts but no way to change them', async () => {
    me.set(
      enrollmentsAdmin({
        roles: ['MENTOR'],
        permissions: ['TRACKS_READ', 'ENROLLMENTS_READ'],
      }),
    );
    api.resolve(getTrack, track({ canEnroll: false }));
    const { settle } = await open();
    await settle();

    await vi.waitFor(() => expect(allById('cohort-item')).toHaveLength(2));
    item(SPRING)?.click();
    await settle();

    expect(byId('create-cohort')).toBeNull();
    expect(byId('track-actions-menu')).toBeNull();
    expect(byId('cohort-add-students')).toBeNull();
    expect(byId('cohort-actions-menu')).toBeNull();
    expect(byId('enrollment-actions-menu')).toBeNull();
    expect(byId('enrollment-menu-take-out')).toBeNull();
  });

  it('keeps the cohorts of an archived track visible and offers no change', async () => {
    api.resolve(getTrack, track({ status: 'ARCHIVED', canEnroll: true }));
    const { settle } = await open();
    await settle();

    await vi.waitFor(() => expect(allById('cohort-item')).toHaveLength(2));
    item(EMPTY)?.click();
    await settle();

    expect(byId('create-cohort')).toBeNull();
    expect(byId('enroll-student')).toBeNull();
    expect(byId('cohort-add-students')).toBeNull();
    expect(byId('cohort-actions-menu')).toBeNull();
  });

  it('asks for the candidates without a cohort by default and for all of them after the switch is turned off', async () => {
    api.resolve(listNewCohortCandidates, {
      items: [candidate('e1', 'Anna Alpha', null)],
      page: 0,
      size: 10,
      totalItems: 1,
    });
    const { settle } = await open();
    await vi.waitFor(() => expect(byId('create-cohort')).not.toBeNull());

    byId('create-cohort')?.click();
    await settle();
    await vi.waitFor(() => expect(api.callsFor(listNewCohortCandidates).length).toBeGreaterThan(0));

    expect(api.callsFor(listNewCohortCandidates)[0].withoutCohort).toBe(true);
    expect(byId('only-without-cohort')?.getAttribute('aria-checked')).toBe('true');

    byId('only-without-cohort')?.click();
    await settle();

    await vi.waitFor(() =>
      expect(api.callsFor(listNewCohortCandidates).some((c) => c.withoutCohort === false)).toBe(
        true,
      ),
    );
  });

  it('refuses a blank name in the create dialog without sending anything', async () => {
    api.resolve(listNewCohortCandidates, { items: [], page: 0, size: 10, totalItems: 0 });
    const { settle } = await open();
    await vi.waitFor(() => expect(byId('create-cohort')).not.toBeNull());
    byId('create-cohort')?.click();
    await settle();
    await vi.waitFor(() => expect(byId('create-cohort-dialog')).not.toBeNull());

    byId('create-cohort-submit')?.click();
    await settle();

    expect(api.callsFor(createCohort)).toHaveLength(0);
    expect(byId('create-cohort-dialog')).not.toBeNull();
    expect(byId('create-cohort-dialog')?.textContent).toContain('Enter a name for the cohort.');
  });

  it('adds students who have no cohort without a move confirmation', async () => {
    api.resolve(listNewCohortCandidates, { items: [], page: 0, size: 10, totalItems: 0 });
    api.resolve(addCohortEnrollments, cohort({ enrollmentCount: 2 }));
    const { settle } = await open();
    await vi.waitFor(() => expect(allById('cohort-item')).toHaveLength(2));
    item(SPRING)?.click();
    await settle();
    await vi.waitFor(() => expect(byId('cohort-add-students')).not.toBeNull());

    byId('cohort-add-students')?.click();
    await settle();

    await vi.waitFor(() => expect(byId('add-students-dialog')).not.toBeNull());
    expect(byId('add-students-submit')?.getAttribute('aria-disabled')).toBe('true');
    byId('add-students-submit')?.click();
    await settle();
    expect(api.callsFor(addCohortEnrollments)).toHaveLength(0);
    expect(byId('move-confirm-dialog')).toBeNull();
  });

  it('takes an enrollment out of its cohort only after the confirmation', async () => {
    api.resolve(removeCohortEnrollment, undefined);
    const { settle } = await open();
    await vi.waitFor(() => expect(byId('enrollment-actions-menu')).not.toBeNull());

    byId('enrollment-actions-menu')?.click();
    await settle();
    await vi.waitFor(() => expect(byId('enrollment-menu-take-out')).not.toBeNull());
    byId('enrollment-menu-take-out')?.click();
    await settle();
    await vi.waitFor(() => expect(byId('take-out-dialog')).not.toBeNull());
    expect(api.callsFor(removeCohortEnrollment)).toHaveLength(0);

    byId('take-out-confirm')?.click();
    await settle();

    await vi.waitFor(() => expect(api.callsFor(removeCohortEnrollment)).toHaveLength(1));
    expect(api.callsFor(removeCohortEnrollment)[0]).toMatchObject({
      id: TRACK_ID,
      cohortId: SPRING,
      enrollmentId: IN_SPRING,
    });
  });

  it('deletes an empty cohort only after the confirmation', async () => {
    api.resolve(deleteCohort, undefined);
    const { settle } = await open();
    await vi.waitFor(() => expect(allById('cohort-item')).toHaveLength(2));
    item(EMPTY)?.click();
    await settle();
    await vi.waitFor(() => expect(byId('cohort-actions-menu')).not.toBeNull());
    byId('cohort-actions-menu')?.click();
    await settle();
    byId('cohort-menu-delete')?.click();
    await settle();
    await vi.waitFor(() => expect(byId('delete-cohort-dialog')).not.toBeNull());

    expect(api.callsFor(deleteCohort)).toHaveLength(0);
    const cancel = [...(byId('delete-cohort-dialog')?.querySelectorAll('button') ?? [])].find(
      (b) => b.textContent?.trim() === 'Cancel',
    );
    cancel?.click();
    await settle();
    expect(api.callsFor(deleteCohort)).toHaveLength(0);
  });

  // The first load shows the track as the page opened it; every later one, the track as it is now.
  const trackChangedMeanwhile = (now: Track) => {
    let loads = 0;
    api.handle(getTrack, () => (loads++ === 0 ? track() : now));
  };

  async function expectRefusalShown(
    element: HTMLElement,
    before: { cohorts: number; enrollments: number },
  ) {
    await vi.waitFor(() => expect(snack()).toMatch(/\S/));
    await vi.waitFor(() => expect(element.textContent).toContain('Quality Engineering'));
    await vi.waitFor(() => {
      expect(api.callsFor(listTrackCohorts).length).toBeGreaterThan(before.cohorts);
      expect(api.callsFor(listTrackEnrollments).length).toBeGreaterThan(before.enrollments);
    });
  }

  async function takeOutRefused(settle: () => Promise<void>) {
    await vi.waitFor(() => expect(byId('enrollment-actions-menu')).not.toBeNull());
    byId('enrollment-actions-menu')?.click();
    await settle();
    await vi.waitFor(() => expect(byId('enrollment-menu-take-out')).not.toBeNull());
    byId('enrollment-menu-take-out')?.click();
    await settle();
    await vi.waitFor(() => expect(byId('take-out-dialog')).not.toBeNull());
    byId('take-out-confirm')?.click();
    await settle();
  }

  async function openAddStudents(settle: () => Promise<void>) {
    await vi.waitFor(() => expect(allById('cohort-item')).toHaveLength(2));
    item(SPRING)?.click();
    await settle();
    await vi.waitFor(() => expect(byId('cohort-add-students')).not.toBeNull());
    byId('cohort-add-students')?.click();
    await settle();
  }

  const refusals: [string, number, string][] = [
    ['404', 404, 'urn:mentorhub:problem:not-found'],
    ['track-archived', 409, 'urn:mentorhub:problem:track-archived'],
  ];

  it.each(refusals)(
    'COH-8.2 explains a %s refusal on taking an enrollment out and shows the track, its cohorts and its enrollments as they are now',
    async (_name, status, type) => {
      trackChangedMeanwhile(track({ name: 'Quality Engineering' }));
      api.reject(removeCohortEnrollment, problem(status, type));
      const { harness, settle } = await open();
      await vi.waitFor(() => expect(byId('enrollment-actions-menu')).not.toBeNull());
      const before = {
        cohorts: api.callsFor(listTrackCohorts).length,
        enrollments: api.callsFor(listTrackEnrollments).length,
      };

      await takeOutRefused(settle);

      expect(api.callsFor(removeCohortEnrollment)).toHaveLength(1);
      await expectRefusalShown(harness.routeNativeElement as HTMLElement, before);
    },
  );

  it.each(refusals)(
    'COH-8.2 explains a %s refusal on loading the candidates of a cohort and shows the track, its cohorts and its enrollments as they are now',
    async (_name, status, type) => {
      trackChangedMeanwhile(track({ name: 'Quality Engineering' }));
      api.reject(listCohortCandidates, problem(status, type));
      const { harness, settle } = await open();
      await vi.waitFor(() => expect(allById('cohort-item')).toHaveLength(2));
      const before = {
        cohorts: api.callsFor(listTrackCohorts).length,
        enrollments: api.callsFor(listTrackEnrollments).length,
      };

      await openAddStudents(settle);

      await vi.waitFor(() => expect(api.callsFor(listCohortCandidates).length).toBeGreaterThan(0));
      await expectRefusalShown(harness.routeNativeElement as HTMLElement, before);
    },
  );

  it('keeps "All enrollments" at the size of the whole track after enrolling a student while a cohort is chosen', async () => {
    let enrolled = false;
    api.handle(listTrackEnrollments, (p) =>
      enrollmentPage([trackEnrollment()], { totalItems: p.cohortId ? 1 : enrolled ? 6 : 5 }),
    );
    api.resolve(listEnrollmentCandidates, {
      items: [enrollmentCandidate()],
      page: 0,
      size: 25,
      totalItems: 1,
    });
    api.handle(enrollStudent, () => {
      enrolled = true;
      return trackEnrollment();
    });
    const { settle } = await open(`/tracks/${TRACK_ID}?cohort=${SPRING}`);
    await vi.waitFor(() => expect(byId('cohort-all')?.textContent).toContain('5 students'));

    byId('enroll-student')?.click();
    await settle();
    await vi.waitFor(() => expect(byId('enroll-cohort')).not.toBeNull());
    document.getElementById('enroll-student-search')?.click();
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
    await vi.waitFor(() => expect(byId('cohort-all')?.textContent).toContain('6 students'));
  });

  describe('a server message under a field of the create cohort dialog', () => {
    const validation = (field: string, message: string) =>
      problemError({
        type: 'urn:mentorhub:problem:validation-failed',
        status: 400,
        title: 'Error',
        errors: [{ field, message }],
      });

    async function submitRefused(field: string, message: string) {
      api.resolve(listNewCohortCandidates, { items: [], page: 0, size: 10, totalItems: 0 });
      api.reject(createCohort, validation(field, message));
      const { settle } = await open();
      await vi.waitFor(() => expect(byId('create-cohort')).not.toBeNull());
      byId('create-cohort')?.click();
      await settle();
      await vi.waitFor(() => expect(byId('create-cohort-dialog')).not.toBeNull());
      setValue(byId('cohort-name') as HTMLInputElement, 'Winter 2027');
      setValue(byId('cohort-start-date') as HTMLInputElement, '2027-01-11');
      byId('create-cohort-submit')?.click();
      await settle();
      await vi.waitFor(() => expect(byId('create-cohort-dialog')?.textContent).toContain(message));
      return settle;
    }

    it('disappears when the user edits the name', async () => {
      const settle = await submitRefused('name', 'The name is too long.');

      setValue(byId('cohort-name') as HTMLInputElement, 'Winter 2028');
      await settle();

      expect(byId('create-cohort-dialog')?.textContent).not.toContain('The name is too long.');
    });

    it('disappears when the user edits the start date', async () => {
      const settle = await submitRefused('startDate', 'The date is out of range.');

      setValue(byId('cohort-start-date') as HTMLInputElement, '2027-01-12');
      await settle();

      expect(byId('create-cohort-dialog')?.textContent).not.toContain('The date is out of range.');
    });
  });
});
