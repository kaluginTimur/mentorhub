import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { assignEnrollmentMentor } from '../../core/api/fn/enrollments/assign-enrollment-mentor';
import { removeEnrollmentMentor } from '../../core/api/fn/enrollments/remove-enrollment-mentor';
import { listMentorCandidates } from '../../core/api/fn/enrollments/list-mentor-candidates';
import { enrollStudent } from '../../core/api/fn/enrollments/enroll-student';
import { listEnrollmentCandidates } from '../../core/api/fn/enrollments/list-enrollment-candidates';
import { listTrackEnrollments } from '../../core/api/fn/enrollments/list-track-enrollments';
import { getTrack } from '../../core/api/fn/tracks/get-track';
import { getTrackHistory } from '../../core/api/fn/tracks/get-track-history';
import { listTrackCoordinatorCandidates } from '../../core/api/fn/tracks/list-track-coordinator-candidates';
import { MeUser } from '../../core/api/models/me-user';
import { Permission } from '../../core/api/models/permission';
import { Track } from '../../core/api/models/track';
import { TrackStatus } from '../../core/api/models/track-status';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { problemError } from '../../core/testing/problem-error';
import { track } from '../tracks/test-data';
import { TrackPage } from '../tracks/track-page';
import {
  CANDIDATE_MENTOR_ID,
  CANDIDATE_STUDENT_ID,
  ENROLLMENT_ID,
  MENTOR_ID,
  TRACK_ID,
  enrolledStudent,
  enrollmentMentor,
  mentorCandidate,
  enrollmentCandidate,
  enrollmentPage,
  enrollmentsAdmin,
  trackEnrollment,
} from './test-data';
import { TrackEnrollments } from './track-enrollments';

@Component({
  imports: [TrackEnrollments],
  template: `<app-track-enrollments
    [trackId]="trackId"
    [canEnroll]="canEnroll()"
    [trackStatus]="trackStatus()"
  />`,
})
class Host {
  readonly trackId = TRACK_ID;
  readonly canEnroll = signal(true);
  readonly trackStatus = signal<TrackStatus>('ACTIVE');
}

const problem = (status: number, type: string) => problemError({ type, status, title: 'Error' });
const today = () => new Intl.DateTimeFormat('en-CA').format(new Date());
const buttonByText = (root: ParentNode, text: string) =>
  [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === text);
const overlay = () =>
  (document.querySelector('.cdk-overlay-container') as HTMLElement | null) ??
  document.createElement('div');
const snack = () => document.querySelector('app-toast-host')?.textContent?.trim() ?? '';
const rowOf = (root: ParentNode, text: string) =>
  [...root.querySelectorAll('li, tr')].find((r) => r.textContent?.includes(text));

// A field is found by its label, as a user finds it.
const fieldLabelled = (root: ParentNode, text: string) => {
  const label = [...root.querySelectorAll('label')].find((l) => l.textContent?.trim() === text);
  const id = label?.getAttribute('for');
  return (
    (id ? root.querySelector<HTMLInputElement>(`#${id}`) : null) ??
    root.querySelector<HTMLInputElement>(`[aria-label="${text}"]`)
  );
};

function type(field: HTMLInputElement | null, value: string) {
  expect(field, 'the field').not.toBeNull();
  if (field) {
    field.value = value;
    field.dispatchEvent(new Event('input', { bubbles: true }));
    field.dispatchEvent(new Event('change', { bubbles: true }));
  }
}

// The plan fixes the "Student" search field and the "Enroll" button, not how a candidate is picked.
// The candidates are a drop-down: the user clicks the Student field to open it.
// With `search`, the user also types to narrow the list.
function openStudentList() {
  const field = fieldLabelled(overlay(), 'Student');
  expect(field, 'the Student field').not.toBeNull();
  field?.click();
}

async function chooseStudent(search: string | null, name: string, settle: () => Promise<void>) {
  openStudentList();
  await settle();
  if (search !== null) {
    type(fieldLabelled(overlay(), 'Student'), search);
    await settle();
  }
  await vi.waitFor(() => {
    const option = [
      ...overlay().querySelectorAll<HTMLElement>('[role=option], [role=radio], li, button'),
    ].find((o) => o.textContent?.includes(name));
    expect(option, `an option for ${name}`).toBeDefined();
    option?.click();
  });
  await settle();
}

describe('TrackEnrollments', () => {
  const api = createApiStub();
  const me = signal<MeUser | null>(enrollmentsAdmin());
  const currentUser = {
    user: me,
    load: vi.fn(),
    hasPermission: (p: Permission) => me()?.permissions.includes(p) ?? false,
  };

  beforeEach(() => {
    api.reset();
    api.resolve(listTrackEnrollments, enrollmentPage([]));
    api.resolve(listEnrollmentCandidates, {
      items: [enrollmentCandidate()],
      page: 0,
      size: 25,
      totalItems: 1,
    });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '', component: Host }]),
        provideApiStub(api),
        { provide: CurrentUserService, useValue: currentUser },
      ],
    });
  });

  afterEach(() => {
    document.querySelector('.cdk-overlay-container')?.replaceChildren();
  });

  async function open(url = '/') {
    const harness = await RouterTestingHarness.create();
    const host = await harness.navigateByUrl(url, Host);
    await harness.fixture.whenStable();
    harness.detectChanges();
    const element = harness.routeNativeElement as HTMLElement;
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
      await harness.fixture.whenStable();
    };
    return { host, element, settle };
  }

  it('shows each enrollment with the name, email and start date, and the marks of ENR-4', async () => {
    api.resolve(
      listTrackEnrollments,
      enrollmentPage([
        trackEnrollment(),
        trackEnrollment({
          id: 'e2',
          student: enrolledStudent({
            id: 's2',
            name: 'Dina Disabled',
            email: 'dina@example.com',
            disabled: true,
          }),
        }),
        trackEnrollment({
          id: 'e3',
          student: enrolledStudent({
            id: 's3',
            name: 'Mia Mentor',
            email: 'mia@example.com',
            studentRole: false,
          }),
        }),
      ]),
    );
    const { element } = await open();

    await vi.waitFor(() => expect(element.textContent).toContain('Sam Student'));
    const active = rowOf(element, 'Sam Student');
    const disabled = rowOf(element, 'Dina Disabled');
    const withoutRole = rowOf(element, 'Mia Mentor');
    expect(active?.textContent).toContain('sam@example.com');
    expect(active?.textContent).toContain('2026');
    expect(active?.textContent).not.toContain('Disabled');
    expect(active?.textContent).not.toContain('No student role');
    expect(disabled?.textContent).toContain('Disabled');
    expect(disabled?.textContent).not.toContain('No student role');
    expect(withoutRole?.textContent).toContain('No student role');
    expect(withoutRole?.textContent).not.toContain('Disabled');
  });

  it('says No students enrolled yet when the track has no enrollment', async () => {
    const { element } = await open();

    await vi.waitFor(() => expect(element.textContent).toContain('No students enrolled yet'));
  });

  it.each<[string, boolean, TrackStatus, boolean]>([
    ['an active track the user may enroll on', true, 'ACTIVE', true],
    ['an active track the user may not enroll on', false, 'ACTIVE', false],
    ['an archived track', true, 'ARCHIVED', false],
  ])('offers "Enroll student" on %s: %s', async (_case, canEnroll, status, offered) => {
    const { host, element, settle } = await open();
    host.canEnroll.set(canEnroll);
    host.trackStatus.set(status);
    await settle();

    // The list is on screen before the absence of the button means anything.
    await vi.waitFor(() => expect(element.textContent).toContain('No students enrolled yet'));
    await vi.waitFor(() =>
      expect(buttonByText(element, 'Enroll student') !== undefined).toBe(offered),
    );
  });

  it('asks for the page named by the enrollmentsPage query parameter', async () => {
    api.resolve(
      listTrackEnrollments,
      enrollmentPage([trackEnrollment()], { page: 1, totalItems: 40 }),
    );

    await open('/?enrollmentsPage=1');

    await vi.waitFor(() =>
      expect(api.callsFor(listTrackEnrollments)).toContainEqual(
        expect.objectContaining({ id: TRACK_ID, page: 1 }),
      ),
    );
  });

  describe('the dialog', () => {
    it('enrolls the chosen candidate from today and lists the new enrollment', async () => {
      api.resolveOnce(listTrackEnrollments, enrollmentPage([]));
      api.handle(enrollStudent, () => {
        api.resolve(
          listTrackEnrollments,
          enrollmentPage([
            trackEnrollment({
              student: enrolledStudent({ id: CANDIDATE_STUDENT_ID, name: 'Olga Candidate' }),
            }),
          ]),
        );
        return trackEnrollment({
          student: enrolledStudent({ id: CANDIDATE_STUDENT_ID, name: 'Olga Candidate' }),
        });
      });
      const { element, settle } = await open();
      await vi.waitFor(() => expect(buttonByText(element, 'Enroll student')).toBeDefined());

      buttonByText(element, 'Enroll student')?.click();
      await settle();
      expect(fieldLabelled(overlay(), 'Start date')?.value, 'today by default').toBe(today());
      await chooseStudent(null, 'Olga Candidate', settle);
      buttonByText(overlay(), 'Enroll')?.click();
      await settle();

      await vi.waitFor(() =>
        expect(api.callsFor(enrollStudent)).toEqual([
          { id: TRACK_ID, body: { studentId: CANDIDATE_STUDENT_ID, startDate: today() } },
        ]),
      );
      await vi.waitFor(() => expect(element.textContent).toContain('Olga Candidate'));
    });

    it('lists the candidates when the Student field is clicked, without typing', async () => {
      const { element, settle } = await open();
      await vi.waitFor(() => expect(buttonByText(element, 'Enroll student')).toBeDefined());

      buttonByText(element, 'Enroll student')?.click();
      await settle();
      openStudentList();
      await settle();

      await vi.waitFor(() => expect(overlay().textContent).toContain('Olga Candidate'));
      const calls = api.callsFor(listEnrollmentCandidates);
      expect(calls).toHaveLength(1);
      expect(calls[0]).toEqual(expect.objectContaining({ id: TRACK_ID }));
      expect(calls[0].q, 'no search').toBeUndefined();
      expect(calls[0].page ?? 0, 'the first page').toBe(0);
    });

    it('sends the start date the user chose and the typed search to the candidates', async () => {
      api.resolve(enrollStudent, trackEnrollment());
      const { element, settle } = await open();
      await vi.waitFor(() => expect(buttonByText(element, 'Enroll student')).toBeDefined());

      buttonByText(element, 'Enroll student')?.click();
      await settle();
      type(fieldLabelled(overlay(), 'Start date'), '2026-10-05');
      await chooseStudent('olga', 'Olga Candidate', settle);
      buttonByText(overlay(), 'Enroll')?.click();
      await settle();

      expect(api.callsFor(listEnrollmentCandidates)).toContainEqual(
        expect.objectContaining({ id: TRACK_ID, q: 'olga' }),
      );
      await vi.waitFor(() =>
        expect(api.callsFor(enrollStudent)).toEqual([
          { id: TRACK_ID, body: { studentId: CANDIDATE_STUDENT_ID, startDate: '2026-10-05' } },
        ]),
      );
    });

    it('enrolls nobody until a candidate is chosen', async () => {
      api.resolve(enrollStudent, trackEnrollment());
      const { element, settle } = await open();
      await vi.waitFor(() => expect(buttonByText(element, 'Enroll student')).toBeDefined());

      buttonByText(element, 'Enroll student')?.click();
      await settle();
      buttonByText(overlay(), 'Enroll')?.click();
      await settle();

      expect(api.callsFor(enrollStudent)).toHaveLength(0);
    });
  });

  describe('mentors', () => {
    const mia = () => enrollmentMentor();
    const onePage = (...items: ReturnType<typeof trackEnrollment>[]) => enrollmentPage(items);
    const mentorCandidates = () =>
      api.resolve(listMentorCandidates, {
        items: [mentorCandidate()],
        page: 0,
        size: 25,
        totalItems: 1,
      });
    // A mentor's avatar is a button whose accessible name contains the mentor's name; a click opens the popover
    // with the removal.
    const avatarButton = (root: ParentNode, name: string) =>
      [...root.querySelectorAll('button')].find((b) => {
        const label = `${b.getAttribute('aria-label') ?? ''} ${b.textContent ?? ''}`;
        return label.includes(name) && !/assign/i.test(label);
      });
    async function openMentorPopover(root: ParentNode, name: string, settle: () => Promise<void>) {
      await vi.waitFor(() =>
        expect(avatarButton(root, name), `the avatar of ${name}`).toBeDefined(),
      );
      avatarButton(root, name)?.click();
      await settle();
    }
    const countOf = (text: string | null | undefined, word: string) =>
      (text ?? '').split(word).length - 1;

    // The plan fixes the "Assign mentor" button, the confirmation's "Remove" and the candidates with their search,
    // not the dialog's wording: the field is looked for by the label "Mentor", or is the first input, and the
    // confirming button is "Assign".
    async function chooseMentor(search: string | null, name: string, settle: () => Promise<void>) {
      const field = () =>
        fieldLabelled(overlay(), 'Mentor') ?? overlay().querySelector<HTMLInputElement>('input');
      field()?.click();
      await settle();
      if (search !== null) {
        type(field(), search);
        await settle();
      }
      await vi.waitFor(() => {
        const option = [
          ...overlay().querySelectorAll<HTMLElement>('[role=option], [role=radio], li, button'),
        ].find((o) => o.textContent?.includes(name));
        expect(option, `an option for ${name}`).toBeDefined();
        option?.click();
      });
      await settle();
    }

    it('shows the mentors of an enrollment with the marks of ENR-4', async () => {
      api.resolve(
        listTrackEnrollments,
        onePage(
          trackEnrollment({
            mentors: [
              mia(),
              enrollmentMentor({
                id: 'm2',
                name: 'Dora Disabled',
                email: 'dora@example.com',
                disabled: true,
              }),
              enrollmentMentor({
                id: 'm3',
                name: 'Rita Retired',
                email: 'rita@example.com',
                mentorRole: false,
              }),
            ],
          }),
        ),
      );

      const { element } = await open();

      await vi.waitFor(() => expect(element.textContent).toContain('Mia Mentor'));
      const row = rowOf(element, 'Sam Student')?.textContent;
      expect(row).toContain('mia@example.com');
      expect(row).toContain('Dora Disabled');
      expect(row).toContain('Rita Retired');
      expect(countOf(row, 'Disabled')).toBe(2); // the name Dora Disabled and the one mark
      expect(countOf(row, 'No mentor role')).toBe(1);
    });

    it('offers "Assign mentor" and a removal per mentor only to a user who may enroll', async () => {
      api.resolve(listTrackEnrollments, onePage(trackEnrollment({ mentors: [mia()] })));
      const { host, element, settle } = await open();
      await vi.waitFor(() => expect(buttonByText(element, 'Assign mentor')).toBeDefined());
      await openMentorPopover(element, 'Mia Mentor', settle);
      await vi.waitFor(() =>
        expect(buttonByText(overlay(), 'Remove'), 'the removal').toBeDefined(),
      );
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      await settle();

      host.canEnroll.set(false);
      await settle();

      await vi.waitFor(() => expect(buttonByText(element, 'Assign mentor')).toBeUndefined());
      await openMentorPopover(element, 'Mia Mentor', settle);
      expect(buttonByText(overlay(), 'Remove')).toBeUndefined();
      expect(element.textContent).toContain('Mia Mentor');
    });

    it('opens the candidates of the enrollment whose "Assign mentor" was clicked', async () => {
      api.resolve(
        listTrackEnrollments,
        onePage(
          trackEnrollment(),
          trackEnrollment({
            id: 'e2',
            student: enrolledStudent({ id: 's2', name: 'Dina Second', email: 'dina@example.com' }),
          }),
        ),
      );
      mentorCandidates();
      const { element, settle } = await open();
      await vi.waitFor(() => expect(element.textContent).toContain('Dina Second'));

      const second = rowOf(element, 'Dina Second');
      expect(second, 'the row').toBeDefined();
      (second && buttonByText(second, 'Assign mentor'))?.click();
      await settle();

      await vi.waitFor(() =>
        expect(api.callsFor(listMentorCandidates)).toContainEqual(
          expect.objectContaining({ id: 'e2' }),
        ),
      );
    });

    it('assigns the chosen candidate and lists the new mentor', async () => {
      api.resolveOnce(listTrackEnrollments, onePage(trackEnrollment()));
      mentorCandidates();
      const assigned = enrollmentMentor({
        id: CANDIDATE_MENTOR_ID,
        name: 'Omar Candidate',
        email: 'omar@example.com',
      });
      api.handle(assignEnrollmentMentor, () => {
        api.resolve(listTrackEnrollments, onePage(trackEnrollment({ mentors: [assigned] })));
        return trackEnrollment({ mentors: [assigned] });
      });
      const { element, settle } = await open();
      await vi.waitFor(() => expect(buttonByText(element, 'Assign mentor')).toBeDefined());

      buttonByText(element, 'Assign mentor')?.click();
      await settle();
      await chooseMentor(null, 'Omar Candidate', settle);
      buttonByText(overlay(), 'Assign')?.click();
      await settle();

      await vi.waitFor(() =>
        expect(api.callsFor(assignEnrollmentMentor)).toEqual([
          { id: ENROLLMENT_ID, userId: CANDIDATE_MENTOR_ID },
        ]),
      );
      await vi.waitFor(() => expect(element.textContent).toContain('Omar Candidate'));
    });

    it('sends the typed search to the candidates', async () => {
      api.resolve(listTrackEnrollments, onePage(trackEnrollment()));
      mentorCandidates();
      const { element, settle } = await open();
      await vi.waitFor(() => expect(buttonByText(element, 'Assign mentor')).toBeDefined());

      buttonByText(element, 'Assign mentor')?.click();
      await settle();
      await chooseMentor('omar', 'Omar Candidate', settle);

      expect(api.callsFor(listMentorCandidates)).toContainEqual(
        expect.objectContaining({ id: ENROLLMENT_ID, q: 'omar' }),
      );
    });

    it('assigns nobody until a candidate is chosen', async () => {
      api.resolve(listTrackEnrollments, onePage(trackEnrollment()));
      mentorCandidates();
      api.resolve(assignEnrollmentMentor, trackEnrollment());
      const { element, settle } = await open();
      await vi.waitFor(() => expect(buttonByText(element, 'Assign mentor')).toBeDefined());

      buttonByText(element, 'Assign mentor')?.click();
      await settle();
      buttonByText(overlay(), 'Assign')?.click();
      await settle();

      expect(api.callsFor(assignEnrollmentMentor)).toHaveLength(0);
    });

    it('removes a mentor after a confirmation and lists the enrollment without them', async () => {
      api.resolveOnce(listTrackEnrollments, onePage(trackEnrollment({ mentors: [mia()] })));
      api.handle(removeEnrollmentMentor, () => {
        api.resolve(listTrackEnrollments, onePage(trackEnrollment()));
        return trackEnrollment();
      });
      const { element, settle } = await open();
      await openMentorPopover(element, 'Mia Mentor', settle);
      await vi.waitFor(() => expect(buttonByText(overlay(), 'Remove')).toBeDefined());
      buttonByText(overlay(), 'Remove')?.click();
      await settle();
      expect(api.callsFor(removeEnrollmentMentor)).toHaveLength(0);
      expect(overlay().textContent).toContain('Mia Mentor');
      buttonByText(overlay(), 'Remove')?.click();
      await settle();

      await vi.waitFor(() =>
        expect(api.callsFor(removeEnrollmentMentor)).toEqual([
          { id: ENROLLMENT_ID, userId: MENTOR_ID },
        ]),
      );
      await vi.waitFor(() => expect(element.textContent).not.toContain('Mia Mentor'));
      expect(element.textContent).toContain('Sam Student');
    });

    it('does not remove a mentor when the confirmation is cancelled', async () => {
      api.resolve(listTrackEnrollments, onePage(trackEnrollment({ mentors: [mia()] })));
      api.resolve(removeEnrollmentMentor, trackEnrollment());
      const { element, settle } = await open();
      await openMentorPopover(element, 'Mia Mentor', settle);
      await vi.waitFor(() => expect(buttonByText(overlay(), 'Remove')).toBeDefined());
      buttonByText(overlay(), 'Remove')?.click();
      await settle();
      buttonByText(overlay(), 'Cancel')?.click();
      await settle();

      expect(api.callsFor(removeEnrollmentMentor)).toHaveLength(0);
      expect(element.textContent).toContain('Mia Mentor');
    });
  });
});

// ENR-7.1 is the behavior of the track page: it reloads the track and its enrollments after the refusal.
describe('a refused enrollment', () => {
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
    api.resolve(getTrackHistory, { items: [], page: 0, size: 25, totalItems: 0 });
    api.resolve(listTrackCoordinatorCandidates, { items: [] });
    api.resolve(listEnrollmentCandidates, {
      items: [enrollmentCandidate()],
      page: 0,
      size: 25,
      totalItems: 1,
    });
    api.resolveOnce(listTrackEnrollments, enrollmentPage([]));
    api.resolve(listTrackEnrollments, enrollmentPage([trackEnrollment()]));
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

  it.each<[string, RegExp | null, Track]>([
    ['track-archived', /archived/i, track({ name: 'Quality Engineering', status: 'ARCHIVED' })],
    ['student-not-eligible', null, track({ name: 'Quality Engineering' })],
    ['student-already-enrolled', null, track({ name: 'Quality Engineering' })],
  ])(
    'explains %s on enrolling and shows the track and its enrollments as they are now',
    async (type_, wording, now) => {
      trackChangedMeanwhile(now);
      api.reject(enrollStudent, problem(409, `urn:mentorhub:problem:${type_}`));
      const { element, settle } = await open();
      await vi.waitFor(() => expect(buttonByText(element, 'Enroll student')).toBeDefined());

      buttonByText(element, 'Enroll student')?.click();
      await settle();
      await chooseStudent(null, 'Olga Candidate', settle);
      buttonByText(overlay(), 'Enroll')?.click();
      await settle();

      expect(api.callsFor(enrollStudent)).toHaveLength(1);
      await vi.waitFor(() => expect(snack()).toMatch(wording ?? /\S/));
      await vi.waitFor(() => expect(element.textContent).toContain('Quality Engineering'));
      await vi.waitFor(() => expect(element.textContent).toContain('Sam Student'));
    },
  );

  it('ENR-7.1 explains track-archived on the first request for candidates and shows the track and its enrollments as they are now', async () => {
    trackChangedMeanwhile(track({ name: 'Quality Engineering', status: 'ARCHIVED' }));
    api.reject(listEnrollmentCandidates, problem(409, 'urn:mentorhub:problem:track-archived'));
    const { element, settle } = await open();
    await vi.waitFor(() => expect(buttonByText(element, 'Enroll student')).toBeDefined());

    buttonByText(element, 'Enroll student')?.click();
    await settle();
    await settle();

    await vi.waitFor(() => expect(snack()).toMatch(/archived/i));
    await vi.waitFor(() => expect(element.textContent).toContain('Quality Engineering'));
    await vi.waitFor(() => expect(element.textContent).toContain('Sam Student'));
    expect(buttonByText(element, 'Enroll student')).toBeUndefined();
  });
});

// ENR-7.1 for the assignment of a mentor, on the track page as for an enrollment.
describe('a refused mentor assignment', () => {
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
    api.resolve(getTrackHistory, { items: [], page: 0, size: 25, totalItems: 0 });
    api.resolve(listTrackCoordinatorCandidates, { items: [] });
    api.resolve(listTrackEnrollments, enrollmentPage([trackEnrollment()]));
    api.resolve(listMentorCandidates, {
      items: [mentorCandidate()],
      page: 0,
      size: 25,
      totalItems: 1,
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

  it('ENR-7.1 explains mentor-not-eligible on assigning and shows the track and its enrollments as they are now', async () => {
    let loads = 0;
    api.handle(getTrack, () => (loads++ === 0 ? track() : track({ name: 'Quality Engineering' })));
    api.reject(assignEnrollmentMentor, problem(409, 'urn:mentorhub:problem:mentor-not-eligible'));
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
    await vi.waitFor(() => expect(buttonByText(element, 'Assign mentor')).toBeDefined());
    const enrollmentLoads = api.callsFor(listTrackEnrollments).length;

    buttonByText(element, 'Assign mentor')?.click();
    await settle();
    const field =
      fieldLabelled(overlay(), 'Mentor') ?? overlay().querySelector<HTMLInputElement>('input');
    field?.click();
    await settle();
    await vi.waitFor(() => {
      const option = [
        ...overlay().querySelectorAll<HTMLElement>('[role=option], [role=radio], li, button'),
      ].find((o) => o.textContent?.includes('Omar Candidate'));
      expect(option, 'an option for Omar Candidate').toBeDefined();
      option?.click();
    });
    await settle();
    buttonByText(overlay(), 'Assign')?.click();
    await settle();

    expect(api.callsFor(assignEnrollmentMentor)).toHaveLength(1);
    await vi.waitFor(() => expect(snack()).toMatch(/\S/));
    await vi.waitFor(() => expect(element.textContent).toContain('Quality Engineering'));
    await vi.waitFor(() =>
      expect(api.callsFor(listTrackEnrollments).length).toBeGreaterThan(enrollmentLoads),
    );
  });
});
