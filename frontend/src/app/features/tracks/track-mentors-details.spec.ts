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
import { CANDIDATE_MENTOR_ID, enrollmentPage, mentorCandidate } from '../enrollments/test-data';
import { MENTOR_ID, TRACK_ID, track, trackAdmin, trackMentor } from './test-data';
import { TrackPage } from './track-page';

const buttonByText = (root: ParentNode, text: string) =>
  [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === text);
const overlay = () =>
  (document.querySelector('.cdk-overlay-container') as HTMLElement | null) ??
  document.createElement('div');
const snack = () => document.querySelector('app-toast-host')?.textContent?.trim() ?? '';

describe('details of the mentors card on the track page', () => {
  const api = createApiStub();
  const me = signal<MeUser | null>(trackAdmin());
  const currentUser = {
    user: me,
    load: vi.fn(),
    hasPermission: (p: Permission) => me()?.permissions.includes(p) ?? false,
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

  async function pickCandidate(settle: () => Promise<void>, name: string) {
    const field = overlay().querySelector<HTMLInputElement>('input');
    field?.click();
    await settle();
    await vi.waitFor(() => {
      const option = [
        ...overlay().querySelectorAll<HTMLElement>('[role=option], [role=radio], li, button'),
      ].find((o) => o.textContent?.includes(name));
      expect(option, `an option for ${name}`).toBeDefined();
      option?.click();
    });
    await settle();
  }

  it('shows each mentor with the email and the marks', async () => {
    api.resolve(
      getTrack,
      track({
        mentors: [
          trackMentor({ disabled: true }),
          trackMentor({
            id: 'a',
            name: 'Rhea Roleless',
            email: 'rhea@example.com',
            mentorRole: false,
          }),
        ],
      }),
    );
    const first = await open();
    const text = first.element.textContent ?? '';

    expect(text).toContain('Mentors');
    expect(text).toContain('Mia Mentor');
    expect(text).toContain('mia@example.com');
    expect(text).toContain('Rhea Roleless');
    expect(text).toContain('Disabled');
    expect(text).toContain('No mentor role');
    expect(text).not.toContain('No mentors');
  });

  it('says No mentors for a track without them', async () => {
    api.resolve(getTrack, track({ mentors: [] }));

    const { element } = await open();

    expect(element.textContent).toContain('No mentors');
  });

  it.each([
    ['a caller who may not enroll', { canEnroll: false }],
    ['an archived track', { status: 'ARCHIVED' as const }],
  ])('offers no naming or removal on %s', async (_name, overrides) => {
    api.resolve(getTrack, track({ mentors: [trackMentor()], ...overrides }));

    const { element } = await open();

    expect(element.textContent).toContain('Mia Mentor');
    expect(buttonByText(element, 'Add mentor')).toBeUndefined();
    expect(element.querySelector('button[aria-label="Remove Mia Mentor"]')).toBeNull();
  });

  it('removes nothing when the confirmation is cancelled', async () => {
    api.resolve(getTrack, track({ mentors: [trackMentor()] }));
    const { element, settle } = await open();

    element.querySelector<HTMLButtonElement>('button[aria-label="Remove Mia Mentor"]')?.click();
    await settle();
    const cancel = buttonByText(overlay(), 'Cancel');
    expect(cancel, 'a way to cancel').toBeDefined();
    cancel?.click();
    await settle();

    expect(api.callsFor(removeTrackMentor)).toHaveLength(0);
    expect(element.textContent).toContain('Mia Mentor');
  });

  it('shows the track the removal answered with', async () => {
    api.resolve(getTrack, track({ mentors: [trackMentor()] }));
    api.resolve(removeTrackMentor, track({ mentors: [] }));
    const { element, settle } = await open();

    element.querySelector<HTMLButtonElement>('button[aria-label="Remove Mia Mentor"]')?.click();
    await settle();
    buttonByText(overlay(), 'Remove')?.click();
    await settle();

    expect(api.callsFor(removeTrackMentor)).toEqual([{ id: TRACK_ID, userId: MENTOR_ID }]);
    await vi.waitFor(() => expect(element.textContent).toContain('No mentors'));
    await vi.waitFor(() => expect(snack()).toMatch(/\S/));
  });

  it('names the picked candidate and shows the track the naming answered with', async () => {
    api.resolve(getTrack, track({ mentors: [] }));
    api.resolve(
      addTrackMentor,
      track({ mentors: [trackMentor({ id: CANDIDATE_MENTOR_ID, name: 'Omar Candidate' })] }),
    );
    const { element, settle } = await open();

    buttonByText(element, 'Add mentor')?.click();
    await settle();
    await pickCandidate(settle, 'Omar Candidate');
    buttonByText(overlay(), 'Add')?.click();
    await settle();

    expect(api.callsFor(addTrackMentor)).toEqual([{ id: TRACK_ID, userId: CANDIDATE_MENTOR_ID }]);
    await vi.waitFor(() => expect(element.textContent).toContain('Omar Candidate'));
    await vi.waitFor(() => expect(element.textContent).not.toContain('No mentors'));
  });

  it('searches the candidates by the typed text', async () => {
    api.resolve(getTrack, track({ mentors: [] }));
    const { element, settle } = await open();

    buttonByText(element, 'Add mentor')?.click();
    await settle();
    const field = overlay().querySelector<HTMLInputElement>('input');
    expect(field, 'the search field').not.toBeNull();
    if (field) {
      field.value = 'omar';
      field.dispatchEvent(new Event('input'));
    }
    await settle();

    await vi.waitFor(() =>
      expect(api.callsFor(listTrackMentorCandidates).some((c) => c.q === 'omar')).toBe(true),
    );
    expect(api.callsFor(listTrackMentorCandidates).every((c) => c.id === TRACK_ID)).toBe(true);
  });
});
