import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { assignCohortMentors } from '../../core/api/fn/cohorts/assign-cohort-mentors';
import { deleteCohort } from '../../core/api/fn/cohorts/delete-cohort';
import { listCohortMentorCandidates } from '../../core/api/fn/cohorts/list-cohort-mentor-candidates';
import { listTrackCohorts } from '../../core/api/fn/cohorts/list-track-cohorts';
import { removeCohortMentor } from '../../core/api/fn/cohorts/remove-cohort-mentor';
import { listTrackEnrollments } from '../../core/api/fn/enrollments/list-track-enrollments';
import { getTrack } from '../../core/api/fn/tracks/get-track';
import { getTrackHistory } from '../../core/api/fn/tracks/get-track-history';
import { listTrackCoordinatorCandidates } from '../../core/api/fn/tracks/list-track-coordinator-candidates';
import { Cohort } from '../../core/api/models/cohort';
import { MeUser } from '../../core/api/models/me-user';
import { Permission } from '../../core/api/models/permission';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { problemError } from '../../core/testing/problem-error';
import { track } from '../tracks/test-data';
import { TrackPage } from '../tracks/track-page';
import {
  CANDIDATE_MENTOR_ID,
  MENTOR_ID,
  TRACK_ID,
  cohortMentor,
  enrollmentPage,
  enrollmentsAdmin,
  mentorAssignmentCandidate,
  trackEnrollment,
} from './test-data';

// The mentors of a cohort on the track page (COH-8): the texts and roles are those of "What the tests rely on" in
// the plan.

const SPRING = '51111111-1111-4111-8111-111111111111';
const EMPTY = '53333333-3333-4333-8333-333333333333';
const OTHER_MENTOR = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';

const problem = (status: number, type: string) => problemError({ type, status, title: 'Error' });
const overlay = () =>
  (document.querySelector('.cdk-overlay-container') as HTMLElement | null) ??
  document.createElement('div');
const nameOf = (e: Element) => (e.getAttribute('aria-label') ?? e.textContent ?? '').trim();
const matches = (name: string, wanted: string | RegExp) =>
  typeof wanted === 'string' ? name === wanted : wanted.test(name);
const buttonNamed = (root: ParentNode, wanted: string | RegExp) =>
  [...root.querySelectorAll('button')].find((b) => matches(nameOf(b), wanted));
const menuItemNamed = (root: ParentNode, wanted: string) =>
  [...root.querySelectorAll<HTMLElement>('[role=menuitem], button')].find(
    (b) => nameOf(b) === wanted,
  );
const dialogNamed = (wanted: string) =>
  [...document.querySelectorAll<HTMLElement>('[role=dialog], [role=alertdialog]')].find(
    (d) =>
      d.getAttribute('aria-label') === wanted ||
      d.querySelector('h1, h2, h3')?.textContent?.trim() === wanted,
  );

const mia = () => cohortMentor();
const dora = () =>
  cohortMentor({ id: 'm2', name: 'Dora Disabled', email: 'dora@example.com', disabled: true });
const cohort = (overrides: Partial<Cohort> = {}): Cohort => ({
  id: SPRING,
  name: 'Spring 2026',
  startDate: '2026-03-01',
  enrollmentCount: 1,
  mentors: [mia(), dora()],
  ...overrides,
});
const emptyCohort = (overrides: Partial<Cohort> = {}) =>
  cohort({ id: EMPTY, name: 'Empty', enrollmentCount: 0, mentors: [], ...overrides });

describe('the mentors of a cohort on the track page', () => {
  const api = createApiStub();
  const me = signal<MeUser | null>(enrollmentsAdmin());
  const currentUser = {
    user: me,
    load: vi.fn(),
    hasPermission: (p: Permission) => me()?.permissions.includes(p) ?? false,
  };

  const trackChangedMeanwhile = (now: ReturnType<typeof track>) => {
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
    api.resolve(listTrackCohorts, { items: [cohort(), emptyCohort()] });
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

  const cohorts = (root: ParentNode) => root.querySelector('nav[aria-label="Cohorts"]') ?? root;

  async function chooseCohort(root: ParentNode, name: string, settle: () => Promise<void>) {
    await vi.waitFor(() =>
      expect(buttonNamed(cohorts(root), new RegExp(`^${name}\\b`))).toBeDefined(),
    );
    buttonNamed(cohorts(root), new RegExp(`^${name}\\b`))?.click();
    await settle();
  }

  async function openActions(root: ParentNode, name: string, settle: () => Promise<void>) {
    await vi.waitFor(() => expect(buttonNamed(root, `Actions for ${name}`)).toBeDefined());
    buttonNamed(root, `Actions for ${name}`)?.click();
    await settle();
  }

  it('shows the mentors of each cohort with the marks, and says when a cohort has none', async () => {
    const { element, settle } = await open();

    await vi.waitFor(() =>
      expect(
        buttonNamed(cohorts(element), 'Mentors of Spring 2026: 2 mentors · 1 inactive'),
      ).toBeDefined(),
    );
    expect(cohorts(element).textContent).toContain('No mentors');
    buttonNamed(cohorts(element), 'Mentors of Spring 2026: 2 mentors · 1 inactive')?.click();
    await settle();
    const popover = dialogNamed('Mentors of Spring 2026');
    expect(popover, 'the mentors popover').toBeDefined();
    const text = popover?.textContent ?? '';
    expect(text).toContain('mia@example.com');
    expect(text.indexOf('Mia Mentor')).toBeLessThan(text.indexOf('Dora Disabled'));
    expect(text).toContain('Disabled');
  });

  it('offers the removal of a mentor of a cohort after a confirmation', async () => {
    api.handle(removeCohortMentor, () => {
      api.resolve(listTrackCohorts, { items: [cohort({ mentors: [dora()] }), emptyCohort()] });
      return cohort({ mentors: [dora()] });
    });
    const { element, settle } = await open();
    await vi.waitFor(() =>
      expect(buttonNamed(cohorts(element), /^Mentors of Spring 2026/)).toBeDefined(),
    );
    buttonNamed(cohorts(element), /^Mentors of Spring 2026/)?.click();
    await settle();

    const remove = buttonNamed(overlay(), 'Remove Mia Mentor as a mentor of Spring 2026');
    expect(remove, 'the icon button of the removal').toBeDefined();
    remove?.click();
    await settle();
    expect(api.callsFor(removeCohortMentor)).toHaveLength(0);
    const confirmation = dialogNamed('Remove Mia Mentor as a mentor of Spring 2026?');
    expect(confirmation, 'the confirmation').toBeDefined();
    expect(confirmation?.textContent).toContain(
      'They stop leading the students of Spring 2026 through the cohort. A student they lead through their own enrollment stays with them. They can be assigned again later.',
    );
    buttonNamed(confirmation as HTMLElement, 'Remove')?.click();
    await settle();

    await vi.waitFor(() =>
      expect(api.callsFor(removeCohortMentor)).toEqual([
        { id: TRACK_ID, cohortId: SPRING, userId: MENTOR_ID },
      ]),
    );
  });

  it('offers a reader the mentors and no way to change them', async () => {
    me.set(enrollmentsAdmin({ permissions: ['ENROLLMENTS_READ'] }));
    const { element, settle } = await open();
    await chooseCohort(element, 'Spring 2026', settle);

    expect(buttonNamed(element, /^Actions for/)).toBeUndefined();
    buttonNamed(cohorts(element), /^Mentors of Spring 2026/)?.click();
    await settle();
    expect(dialogNamed('Mentors of Spring 2026')).toBeDefined();
    expect(buttonNamed(overlay(), /^Remove /)).toBeUndefined();
  });

  it('assigns several mentors with one request from the dialog of the candidates', async () => {
    api.resolve(listCohortMentorCandidates, {
      items: [
        mentorAssignmentCandidate({ students: 3, cohorts: 1 }),
        mentorAssignmentCandidate({
          id: OTHER_MENTOR,
          name: 'Pavel Candidate',
          email: 'pavel@example.com',
          students: 1,
          cohorts: 0,
        }),
      ],
      page: 0,
      size: 25,
      totalItems: 2,
    });
    api.handle(assignCohortMentors, () => {
      api.resolve(listTrackCohorts, { items: [cohort(), emptyCohort()] });
      return cohort();
    });
    const { element, settle } = await open();
    await chooseCohort(element, 'Spring 2026', settle);
    await openActions(element, 'Spring 2026', settle);
    menuItemNamed(overlay(), 'Assign mentors')?.click();
    await settle();

    await vi.waitFor(() => expect(dialogNamed('Assign mentors to Spring 2026')).toBeDefined());
    const dialog = dialogNamed('Assign mentors to Spring 2026') as HTMLElement;
    await vi.waitFor(() => expect(dialog.textContent).toContain('Omar Candidate'));
    expect(dialog.textContent).toContain('3 students · 1 cohort');
    expect(dialog.textContent).toContain('1 student · 0 cohorts');
    expect(dialog.textContent).toContain('Choose at least one mentor.');
    expect(buttonNamed(dialog, 'Assign mentors')?.getAttribute('aria-disabled')).toBe('true');
    (buttonNamed(dialog, 'Assign mentors') as HTMLElement).click();
    await settle();
    expect(api.callsFor(assignCohortMentors)).toHaveLength(0);

    for (const name of ['Omar Candidate', 'Pavel Candidate']) {
      dialog.querySelector<HTMLElement>(`[aria-label="Choose ${name}"]`)?.click();
      await settle();
    }
    expect(dialog.textContent).toContain('2 chosen');
    buttonNamed(dialog, 'Assign 2 mentors')?.click();
    await settle();

    await vi.waitFor(() =>
      expect(api.callsFor(assignCohortMentors)).toEqual([
        {
          id: TRACK_ID,
          cohortId: SPRING,
          body: { userIds: [CANDIDATE_MENTOR_ID, OTHER_MENTOR] },
        },
      ]),
    );
    await vi.waitFor(() => expect(dialogNamed('Assign mentors to Spring 2026')).toBeUndefined());
  });

  it('says in the deletion of a cohort that its mentors are removed with it', async () => {
    api.resolve(listTrackCohorts, {
      items: [cohort(), emptyCohort({ mentors: [mia(), dora()] })],
    });
    api.resolve(deleteCohort, undefined);
    const { element, settle } = await open();
    await chooseCohort(element, 'Empty', settle);
    await openActions(element, 'Empty', settle);
    menuItemNamed(overlay(), 'Delete')?.click();
    await settle();

    const confirmation = dialogNamed('Delete Empty?');
    expect(confirmation, 'the confirmation').toBeDefined();
    expect(confirmation?.textContent).toContain(
      'The cohort has no students. Its 2 mentors, Mia Mentor, Dora Disabled, are removed with it. Its entries in the history stay.',
    );
  });

  it('COH-8.2 explains mentor-not-eligible on assigning mentors and shows the cohorts and the enrollments as they are now', async () => {
    trackChangedMeanwhile(track({ name: 'Quality Engineering' }));
    api.resolve(listCohortMentorCandidates, {
      items: [mentorAssignmentCandidate()],
      page: 0,
      size: 25,
      totalItems: 1,
    });
    api.reject(assignCohortMentors, problem(409, 'urn:mentorhub:problem:mentor-not-eligible'));
    const { element, settle } = await open();
    await chooseCohort(element, 'Spring 2026', settle);
    await openActions(element, 'Spring 2026', settle);
    menuItemNamed(overlay(), 'Assign mentors')?.click();
    await settle();
    await vi.waitFor(() => expect(dialogNamed('Assign mentors to Spring 2026')).toBeDefined());
    const dialog = dialogNamed('Assign mentors to Spring 2026') as HTMLElement;
    await vi.waitFor(() => expect(dialog.textContent).toContain('Omar Candidate'));
    const cohortLoads = api.callsFor(listTrackCohorts).length;
    const enrollmentLoads = api.callsFor(listTrackEnrollments).length;
    dialog.querySelector<HTMLElement>('[aria-label="Choose Omar Candidate"]')?.click();
    await settle();
    buttonNamed(dialog, 'Assign 1 mentor')?.click();
    await settle();

    await vi.waitFor(() =>
      expect(document.body.textContent).toContain(
        'That user can no longer be assigned: they must be active, hold the mentor role and be a mentor of this track. No mentor was assigned. The cohorts are shown as they are now.',
      ),
    );
    expect(dialogNamed('Assign mentors to Spring 2026')).toBeUndefined();
    await vi.waitFor(() => {
      expect(api.callsFor(listTrackCohorts).length).toBeGreaterThan(cohortLoads);
      expect(api.callsFor(listTrackEnrollments).length).toBeGreaterThan(enrollmentLoads);
    });
  });
});
