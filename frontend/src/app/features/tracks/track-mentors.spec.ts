import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { listTrackEnrollments } from '../../core/api/fn/enrollments/list-track-enrollments';
import { addTrackMentor } from '../../core/api/fn/tracks/add-track-mentor';
import { getTrack } from '../../core/api/fn/tracks/get-track';
import { getTrackHistory } from '../../core/api/fn/tracks/get-track-history';
import { listTrackCoordinatorCandidates } from '../../core/api/fn/tracks/list-track-coordinator-candidates';
import { listTrackMentorCandidates } from '../../core/api/fn/tracks/list-track-mentor-candidates';
import { removeTrackMentor } from '../../core/api/fn/tracks/remove-track-mentor';
import { MeUser } from '../../core/api/models/me-user';
import { Permission } from '../../core/api/models/permission';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { problemError } from '../../core/testing/problem-error';
import { enrollmentPage, mentorCandidate } from '../enrollments/test-data';
import { MENTOR_ID, TRACK_ID, track, trackAdmin, trackMentor } from './test-data';
import { TrackPage } from './track-page';

const problem = (status: number, type: string) => problemError({ type, status, title: 'Error' });
const buttonByText = (root: ParentNode, text: string) =>
  [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === text);
const overlay = () =>
  (document.querySelector('.cdk-overlay-container') as HTMLElement | null) ??
  document.createElement('div');
const snack = () => document.querySelector('app-toast-host')?.textContent?.trim() ?? '';

// A field is found by its label, as a user finds it.
const fieldLabelled = (root: ParentNode, text: string) => {
  const label = [...root.querySelectorAll('label')].find((l) => l.textContent?.trim() === text);
  const id = label?.getAttribute('for');
  return (
    (id ? root.querySelector<HTMLInputElement>(`#${id}`) : null) ??
    root.querySelector<HTMLInputElement>(`[aria-label="${text}"]`)
  );
};

// The mentors of a track on the track page: the refusals of ENR-7.1.
describe('the mentors of a track on the track page', () => {
  const api = createApiStub();
  const me = signal<MeUser | null>(trackAdmin());
  const currentUser = {
    user: me,
    load: vi.fn(),
    hasPermission: (p: Permission) => me()?.permissions.includes(p) ?? false,
  };

  // The first load shows the track as the page opened it; every later one, the track as it is now.
  const trackChangedMeanwhile = (now: ReturnType<typeof track>) => {
    let loads = 0;
    api.handle(getTrack, () => (loads++ === 0 ? track({ mentors: [trackMentor()] }) : now));
  };

  beforeEach(() => {
    api.reset();
    me.set(trackAdmin());
    api.resolve(getTrackHistory, { items: [], page: 0, size: 25, totalItems: 0 });
    api.resolve(listTrackCoordinatorCandidates, { items: [] });
    api.resolve(listTrackEnrollments, enrollmentPage([]));
    api.resolve(listTrackMentorCandidates, {
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

  it('ENR-7.1 explains mentor-not-eligible on naming a mentor and shows the track and its enrollments as they are now', async () => {
    trackChangedMeanwhile(track({ name: 'Quality Engineering', mentors: [trackMentor()] }));
    api.reject(addTrackMentor, problem(409, 'urn:mentorhub:problem:mentor-not-eligible'));
    const { element, settle } = await open();
    const enrollmentLoads = api.callsFor(listTrackEnrollments).length;

    const add = buttonByText(element, 'Add mentor');
    expect(add, 'the "Add mentor" button').toBeDefined();
    add?.click();
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
    buttonByText(overlay(), 'Add')?.click();
    await settle();

    expect(api.callsFor(addTrackMentor)).toHaveLength(1);
    await vi.waitFor(() => expect(snack()).toMatch(/\S/));
    await vi.waitFor(() => expect(element.textContent).toContain('Quality Engineering'));
    await vi.waitFor(() =>
      expect(api.callsFor(listTrackEnrollments).length).toBeGreaterThan(enrollmentLoads),
    );
  });

  it('ENR-7.1 explains mentor-leads-enrollments on removing a mentor and shows the track and its enrollments as they are now', async () => {
    trackChangedMeanwhile(track({ name: 'Quality Engineering', mentors: [trackMentor()] }));
    api.reject(removeTrackMentor, problem(409, 'urn:mentorhub:problem:mentor-leads-enrollments'));
    const { element, settle } = await open();
    const enrollmentLoads = api.callsFor(listTrackEnrollments).length;

    const remove = element.querySelector<HTMLButtonElement>(
      'button[aria-label="Remove Mia Mentor"]',
    );
    expect(remove, 'the "Remove" button of the mentor').not.toBeNull();
    remove?.click();
    await settle();
    const confirm = buttonByText(overlay(), 'Remove');
    expect(confirm, 'the confirmation of the removal').toBeDefined();
    confirm?.click();
    await settle();

    expect(api.callsFor(removeTrackMentor)).toEqual([{ id: TRACK_ID, userId: MENTOR_ID }]);
    await vi.waitFor(() => expect(snack()).toMatch(/\S/));
    await vi.waitFor(() => expect(element.textContent).toContain('Quality Engineering'));
    await vi.waitFor(() =>
      expect(api.callsFor(listTrackEnrollments).length).toBeGreaterThan(enrollmentLoads),
    );
  });

  it('ENR-7.1 explains track-archived on the first request for candidates for a mentor and shows the track as it is now', async () => {
    trackChangedMeanwhile(
      track({ name: 'Quality Engineering', status: 'ARCHIVED', mentors: [trackMentor()] }),
    );
    api.reject(listTrackMentorCandidates, problem(409, 'urn:mentorhub:problem:track-archived'));
    const { element, settle } = await open();

    const add = buttonByText(element, 'Add mentor');
    expect(add, 'the "Add mentor" button').toBeDefined();
    add?.click();
    await settle();
    await settle();

    await vi.waitFor(() => expect(snack()).toMatch(/archived/i));
    await vi.waitFor(() => expect(element.textContent).toContain('Quality Engineering'));
    expect(buttonByText(element, 'Add mentor')).toBeUndefined();
  });
});
