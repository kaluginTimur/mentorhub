import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { assignEnrollmentMentor } from '../../core/api/fn/enrollments/assign-enrollment-mentor';
import { listMentorCandidates } from '../../core/api/fn/enrollments/list-mentor-candidates';
import { listTrackEnrollments } from '../../core/api/fn/enrollments/list-track-enrollments';
import { getTrack } from '../../core/api/fn/tracks/get-track';
import { getTrackHistory } from '../../core/api/fn/tracks/get-track-history';
import { listTrackCoordinatorCandidates } from '../../core/api/fn/tracks/list-track-coordinator-candidates';
import { addTrackMentor } from '../../core/api/fn/tracks/add-track-mentor';
import { listTrackMentorCandidates } from '../../core/api/fn/tracks/list-track-mentor-candidates';
import { removeTrackMentor } from '../../core/api/fn/tracks/remove-track-mentor';
import { MeUser } from '../../core/api/models/me-user';
import { Permission } from '../../core/api/models/permission';
import { TrackEnrollment } from '../../core/api/models/track-enrollment';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { problemError } from '../../core/testing/problem-error';
import { enrollmentPage, mentorCandidate, trackEnrollment } from '../enrollments/test-data';
import { MENTOR_ID, TRACK_ID, track, trackAdmin, trackMentor } from './test-data';
import { TrackPage } from './track-page';

const OTHER_MENTOR_ID = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';

const text = (el: Element | null | undefined) =>
  (el?.textContent ?? '').replace(/\s+/g, ' ').trim();
const buttonByText = (root: ParentNode, label: string) =>
  [...root.querySelectorAll('button')].find((b) => text(b) === label);
const overlay = () =>
  (document.querySelector('.cdk-overlay-container') as HTMLElement | null) ??
  document.createElement('div');

function students(count: number, prefix = 'Student'): TrackEnrollment[] {
  return Array.from({ length: count }, (_, i) =>
    trackEnrollment({
      id: `00000000-0000-4000-8000-${String(i).padStart(12, '0')}`,
      student: {
        id: `10000000-0000-4000-8000-${String(i).padStart(12, '0')}`,
        name: `${prefix} ${String(i + 1).padStart(2, '0')}`,
        email: `${prefix.toLowerCase()}${i + 1}@example.com`,
        disabled: false,
        studentRole: true,
      },
    }),
  );
}

// The students under the mentors of a track: the client part of ENR-7 (ENR-15).
describe('the students of the mentors on the track page', () => {
  const api = createApiStub();
  const me = signal<MeUser | null>(trackAdmin());
  const currentUser = {
    user: me,
    load: vi.fn(),
    hasPermission: (p: Permission) => me()?.permissions.includes(p) ?? false,
  };
  const mia = () => trackMentor({ students: 3 });
  const omar = () =>
    trackMentor({ id: OTHER_MENTOR_ID, name: 'Omar Mentor', email: 'omar@example.com' });
  const byMentor = (id: string) =>
    api.callsFor(listTrackEnrollments).filter((c) => c?.mentor === id);

  // Every mentor leads the same students unless a test sets its own handler.
  function leads(all: TrackEnrollment[]) {
    api.handle(listTrackEnrollments, (p) => {
      if (!p?.mentor) return enrollmentPage([]);
      const size = p.size ?? 25;
      const page = p.page ?? 0;
      return enrollmentPage(all.slice(page * size, (page + 1) * size), {
        page,
        size,
        totalItems: all.length,
      });
    });
  }

  beforeEach(() => {
    api.reset();
    me.set(trackAdmin());
    api.resolve(getTrackHistory, { items: [], page: 0, size: 25, totalItems: 0 });
    api.resolve(listTrackCoordinatorCandidates, { items: [] });
    api.resolve(getTrack, track({ mentors: [mia(), omar()] }));
    leads(students(3));
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
    const chevron = (name: string) =>
      element.querySelector<HTMLButtonElement>(`button[aria-label="Students of ${name}"]`);
    const expand = async (name: string) => {
      const button = chevron(name);
      expect(button, `the chevron of ${name}`).not.toBeNull();
      button?.click();
      await settle();
    };
    return { element, settle, chevron, expand };
  }

  it('shows the number of students of each mentor and a chevron only for a mentor who leads some', async () => {
    api.resolve(
      getTrack,
      track({
        mentors: [
          trackMentor({ students: 1 }),
          trackMentor({ id: OTHER_MENTOR_ID, name: 'Omar Mentor', students: 0 }),
        ],
      }),
    );
    const { element, chevron } = await open();

    expect(text(element)).toContain('1 student');
    expect(text(element)).not.toContain('1 students');
    expect(text(element)).toContain('No students');
    expect(chevron('Mia Mentor')?.getAttribute('aria-expanded')).toBe('false');
    expect(chevron('Omar Mentor')).toBeNull();
  });

  it('writes N students for a mentor of several', async () => {
    const { element } = await open();

    expect(text(element)).toContain('3 students');
  });

  it('shows the counts and no chevrons to a user without ENROLLMENTS_READ', async () => {
    me.set(
      trackAdmin({
        permissions: ['USERS_READ', 'TRACKS_READ', 'TRACKS_MANAGE', 'ENROLLMENTS_MANAGE'],
      }),
    );
    const { element, chevron } = await open();

    expect(text(element)).toContain('3 students');
    expect(chevron('Mia Mentor')).toBeNull();
    expect(byMentor(MENTOR_ID)).toHaveLength(0);
  });

  it('loads the students of a mentor on request, narrowed to the mentor, with the headers and no caption', async () => {
    const { element, expand, chevron } = await open();
    expect(byMentor(MENTOR_ID)).toHaveLength(0);

    await expand('Mia Mentor');

    await vi.waitFor(() => expect(text(element)).toContain('Student 03'));
    expect(byMentor(MENTOR_ID)[0]).toMatchObject({ id: TRACK_ID, mentor: MENTOR_ID, size: 100 });
    expect(chevron('Mia Mentor')?.getAttribute('aria-expanded')).toBe('true');
    const headers = [...element.querySelectorAll('[role=columnheader], th')].map((h) => text(h));
    expect(headers).toEqual(expect.arrayContaining(['Student', 'Email', 'Started']));
    expect(text(element)).not.toMatch(/students? of Mia Mentor on this track/);
    expect(text(element)).not.toContain('ordered by name');
    expect(text(element)).toContain('student1@example.com');
    expect(text(element)).not.toContain('End of the list');
  });

  it('shows all the students of a mentor, following the pages until all are loaded', async () => {
    leads(students(150));
    const { element, expand } = await open();

    await expand('Mia Mentor');

    await vi.waitFor(() => expect(text(element)).toContain('Student 150'));
    expect(byMentor(MENTOR_ID).map((c) => c?.page)).toEqual([0, 1]);
    expect(text(element)).toContain('Student 01');
    expect(text(element)).toContain('End of the list: all 150 students shown');
  });

  it('shows the marks of a student under a mentor', async () => {
    const marked = students(2);
    marked[0].student.disabled = true;
    marked[1].student.studentRole = false;
    leads(marked);
    const { element, expand } = await open();

    await expand('Mia Mentor');

    await vi.waitFor(() => expect(text(element)).toContain('Student 01'));
    expect(text(element)).toContain('Disabled');
    expect(text(element)).toContain('No student role');
  });

  it('lets several mentors stay open at once', async () => {
    api.resolve(getTrack, track({ mentors: [mia(), trackMentor({ ...omar(), students: 2 })] }));
    const { element, expand, chevron } = await open();

    await expand('Mia Mentor');
    await expand('Omar Mentor');

    await vi.waitFor(() => expect(byMentor(OTHER_MENTOR_ID)).toHaveLength(1));
    expect(chevron('Mia Mentor')?.getAttribute('aria-expanded')).toBe('true');
    expect(chevron('Omar Mentor')?.getAttribute('aria-expanded')).toBe('true');
    expect(text(element)).not.toContain('on this track, ordered by name');
  });

  it('hides the students when the chevron is used again', async () => {
    const { element, expand, chevron } = await open();
    await expand('Mia Mentor');
    await vi.waitFor(() => expect(text(element)).toContain('Student 03'));

    await expand('Mia Mentor');

    expect(chevron('Mia Mentor')?.getAttribute('aria-expanded')).toBe('false');
    expect(text(element)).not.toContain('Student 03');
  });

  it('shows the foot and the bottom control only for more than 8 students, and the control hides the list and focuses the top chevron', async () => {
    leads(students(9));
    const { element, expand, chevron, settle } = await open();

    await expand('Mia Mentor');

    await vi.waitFor(() =>
      expect(text(element)).toContain('End of the list: all 9 students shown'),
    );
    const bottom = element.querySelector<HTMLButtonElement>(
      'button[aria-label="Hide the students of Mia Mentor (back to top)"]',
    );
    expect(bottom, 'the bottom control').not.toBeNull();
    bottom?.click();
    await settle();

    expect(chevron('Mia Mentor')?.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(chevron('Mia Mentor'));
  });

  it('shows a progress bar named after the mentor while the students load, and no foot before they have', async () => {
    let release: (() => void) | undefined;
    const all = students(9);
    api.handle(listTrackEnrollments, async (p) => {
      if (!p?.mentor) return enrollmentPage([]);
      await new Promise<void>((resolve) => (release = resolve));
      return enrollmentPage(all, { size: 100, totalItems: 9 });
    });
    const { element, expand, settle } = await open();

    await expand('Mia Mentor');

    const bar = element.querySelector('[role=progressbar]');
    expect(bar?.getAttribute('aria-label') ?? text(bar)).toContain(
      'Loading the students of Mia Mentor',
    );
    expect(text(element)).toContain('Loading the students of Mia Mentor...');
    expect(text(element)).not.toContain('End of the list');
    release?.();
    await settle();
    await vi.waitFor(() =>
      expect(text(element)).toContain('End of the list: all 9 students shown'),
    );
    expect(element.querySelector('[role=progressbar]')).toBeNull();
  });

  it('reports a failed load in the block with Retry, and the mentors stay usable', async () => {
    let failed = false;
    api.handle(listTrackEnrollments, (p) => {
      if (!p?.mentor) return enrollmentPage([]);
      if (!failed) {
        failed = true;
        throw problemError({ type: 'about:blank', status: 500, title: 'Error' });
      }
      return enrollmentPage(students(3), { size: 100, totalItems: 3 });
    });
    const { element, expand, settle } = await open();

    await expand('Mia Mentor');

    await vi.waitFor(() => {
      const alert = element.querySelector('[role=alert]');
      expect(text(alert)).toContain('Could not load the students of Mia Mentor.');
    });
    expect(element.querySelector('button[aria-label="Remove Mia Mentor"]')).not.toBeNull();
    const retry = buttonByText(element, 'Retry');
    expect(retry, 'the Retry button').toBeDefined();
    retry?.click();
    await settle();
    await vi.waitFor(() => expect(text(element)).toContain('Student 03'));
  });

  it('says so when a mentor whose count was stale leads no students', async () => {
    leads([]);
    const { element, expand } = await open();

    await expand('Mia Mentor');

    await vi.waitFor(() =>
      expect(text(element)).toContain('Mia Mentor leads no students on this track.'),
    );
  });

  it('shows the mentors of the track as returned and reloads every open students list after a mentor of the track is removed', async () => {
    const { element, expand, settle } = await open();
    await expand('Mia Mentor');
    await vi.waitFor(() => expect(text(element)).toContain('Student 03'));
    api.resolve(removeTrackMentor, track({ mentors: [mia()] }));
    const loads = byMentor(MENTOR_ID).length;

    element.querySelector<HTMLButtonElement>('button[aria-label="Remove Omar Mentor"]')?.click();
    await settle();
    buttonByText(overlay(), 'Remove')?.click();
    await settle();

    expect(api.callsFor(removeTrackMentor)).toHaveLength(1);
    await vi.waitFor(() => expect(text(element)).not.toContain('Omar Mentor'));
    await vi.waitFor(() => expect(byMentor(MENTOR_ID).length).toBeGreaterThan(loads));
  });

  async function pickAndConfirm(settle: () => Promise<void>, name: string, confirm: string) {
    const field = () => overlay().querySelector<HTMLInputElement>('input');
    field()?.click();
    await settle();
    await vi.waitFor(() => {
      const option = [
        ...overlay().querySelectorAll<HTMLElement>('[role=option], [role=radio], li, button'),
      ].find((o) => o.textContent?.includes(name));
      expect(option, `an option for ${name}`).toBeDefined();
      option?.click();
    });
    await settle();
    buttonByText(overlay(), confirm)?.click();
    await settle();
  }

  it('reloads the track and every open students list after a mentor is assigned to an enrollment', async () => {
    api.handle(listTrackEnrollments, (p) => {
      if (!p?.mentor) return enrollmentPage([trackEnrollment()]);
      return enrollmentPage(students(3), { size: 100, totalItems: 3 });
    });
    api.resolve(listMentorCandidates, {
      items: [mentorCandidate()],
      page: 0,
      size: 25,
      totalItems: 1,
    });
    api.handle(assignEnrollmentMentor, () => {
      api.resolve(getTrack, track({ mentors: [trackMentor({ students: 4 }), omar()] }));
      return trackEnrollment();
    });
    const { element, expand, settle } = await open();
    await expand('Mia Mentor');
    await vi.waitFor(() => expect(text(element)).toContain('Student 03'));
    expect(text(element)).toContain('3 students');
    const loads = byMentor(MENTOR_ID).length;

    await vi.waitFor(() => expect(buttonByText(element, 'Assign mentor')).toBeDefined());
    buttonByText(element, 'Assign mentor')?.click();
    await settle();
    await pickAndConfirm(settle, 'Omar Candidate', 'Assign');

    expect(api.callsFor(assignEnrollmentMentor)).toHaveLength(1);
    await vi.waitFor(() => expect(text(element)).toContain('4 students'));
    await vi.waitFor(() => expect(byMentor(MENTOR_ID).length).toBeGreaterThan(loads));
  });

  it('reloads every open students list after a mentor is named for the track', async () => {
    api.resolve(listTrackMentorCandidates, {
      items: [mentorCandidate()],
      page: 0,
      size: 25,
      totalItems: 1,
    });
    api.resolve(addTrackMentor, track({ mentors: [mia(), omar()] }));
    const { element, expand, settle } = await open();
    await expand('Mia Mentor');
    await vi.waitFor(() => expect(text(element)).toContain('Student 03'));
    const loads = byMentor(MENTOR_ID).length;

    buttonByText(element, 'Add mentor')?.click();
    await settle();
    await pickAndConfirm(settle, 'Omar Candidate', 'Add');

    expect(api.callsFor(addTrackMentor)).toHaveLength(1);
    await vi.waitFor(() => expect(byMentor(MENTOR_ID).length).toBeGreaterThan(loads));
  });

  it('starts with every mentor collapsed when the route moves to another track', async () => {
    const OTHER_TRACK_ID = '33333333-3333-4333-8333-444444444444';
    api.handle(getTrack, (p) =>
      p?.id === OTHER_TRACK_ID
        ? track({ id: OTHER_TRACK_ID, code: 'SWE', name: 'Software', mentors: [mia(), omar()] })
        : track({ mentors: [mia(), omar()] }),
    );
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
    const chevron = (name: string) =>
      element.querySelector<HTMLButtonElement>(`button[aria-label="Students of ${name}"]`);
    chevron('Mia Mentor')?.click();
    await settle();
    await vi.waitFor(() => expect(text(element)).toContain('Student 03'));
    expect(chevron('Mia Mentor')?.getAttribute('aria-expanded')).toBe('true');

    await harness.navigateByUrl(`/tracks/${OTHER_TRACK_ID}`);
    await settle();

    await vi.waitFor(() => expect(text(element)).toContain('Software'));
    expect(chevron('Mia Mentor')?.getAttribute('aria-expanded')).toBe('false');
    expect(chevron('Omar Mentor')).toBeNull();
    expect(text(element)).not.toContain('Student 03');
    expect(byMentor(MENTOR_ID).filter((c) => c?.id === OTHER_TRACK_ID)).toHaveLength(0);
  });
});
