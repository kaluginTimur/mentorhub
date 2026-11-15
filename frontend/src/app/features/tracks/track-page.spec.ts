import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { addTrackCoordinator } from '../../core/api/fn/tracks/add-track-coordinator';
import { getTrack } from '../../core/api/fn/tracks/get-track';
import { getTrackHistory } from '../../core/api/fn/tracks/get-track-history';
import { listTrackCoordinatorCandidates } from '../../core/api/fn/tracks/list-track-coordinator-candidates';
import { removeTrackCoordinator } from '../../core/api/fn/tracks/remove-track-coordinator';
import { MeUser } from '../../core/api/models/me-user';
import { Permission } from '../../core/api/models/permission';
import { Track } from '../../core/api/models/track';
import { TrackHistoryPage } from '../../core/api/models/track-history-page';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { problemError } from '../../core/testing/problem-error';
import { CANDIDATE_ID, TRACK_ID, candidate, track, trackAdmin } from './test-data';
import { TrackPage } from './track-page';

const problem = (status: number, type: string) => problemError({ type, status, title: 'Error' });
const emptyHistory: TrackHistoryPage = { items: [], page: 0, size: 25, totalItems: 0 };
const buttonByText = (root: ParentNode, text: string) =>
  [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === text);

describe('TrackPage', () => {
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
    api.resolve(getTrackHistory, emptyHistory);
    api.resolve(listTrackCoordinatorCandidates, { items: [candidate()] });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'tracks/:id', component: TrackPage },
          { path: '', children: [] },
        ]),
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

  // The first load shows the track as the page opened it; every later one, the track as another change left it.
  const trackChangedMeanwhile = (now: Track) => {
    let loads = 0;
    api.handle(getTrack, () => (loads++ === 0 ? track() : now));
  };

  const overlay = () => document.querySelector('.cdk-overlay-container') as HTMLElement;
  const snack = () => document.querySelector('app-toast-host')?.textContent?.trim() ?? '';

  // The plan fixes the "Add coordinator" button, not how a candidate is picked: a native select, a Spartan select,
  // or a clickable option; then "Add" when the dialog has it.
  async function chooseCandidate(name: string, settle: () => Promise<void>) {
    const dialog = overlay();
    const select = dialog.querySelector('select');
    const spartanTrigger = dialog.querySelector<HTMLElement>('hlm-select-trigger button');
    if (select) {
      const option = [...select.options].find((o) => o.textContent?.includes(name));
      expect(option, `an option for ${name}`).toBeDefined();
      select.value = option?.value ?? '';
      select.dispatchEvent(new Event('change'));
    } else if (spartanTrigger) {
      spartanTrigger.click();
      await settle();
      const item = [...document.querySelectorAll<HTMLElement>('hlm-select-item')].find((i) =>
        i.textContent?.includes(name),
      );
      expect(item, `an option for ${name}`).toBeDefined();
      item?.click();
    } else {
      const option = [
        ...dialog.querySelectorAll<HTMLElement>('[role=option], [role=radio], label, li, button'),
      ].find((o) => o.textContent?.includes(name));
      expect(option, `an option for ${name}`).toBeDefined();
      option?.click();
    }
    await settle();
    const add = buttonByText(dialog, 'Add');
    if (add) {
      add.click();
      await settle();
    }
  }

  describe('a refused change', () => {
    it('TRK-10.1 explains track-archived on removing a coordinator and shows the track as it is now', async () => {
      trackChangedMeanwhile(track({ name: 'Quality Engineering', status: 'ARCHIVED' }));
      api.reject(removeTrackCoordinator, problem(409, 'urn:mentorhub:problem:track-archived'));
      const { element, settle } = await open();

      const remove = buttonByText(element, 'Remove');
      expect(remove, 'the "Remove" button of the coordinator').toBeDefined();
      remove?.click();
      await settle();

      await vi.waitFor(() => expect(snack()).toMatch(/archived/i));
      await vi.waitFor(() => expect(element.textContent).toContain('Quality Engineering'));
    });

    it('TRK-10.1 explains coordinator-not-eligible on naming a coordinator and shows the track as it is now', async () => {
      trackChangedMeanwhile(track({ name: 'Quality Engineering' }));
      api.reject(
        addTrackCoordinator,
        problem(409, 'urn:mentorhub:problem:coordinator-not-eligible'),
      );
      const { element, settle } = await open();

      const add = buttonByText(element, 'Add coordinator');
      expect(add, 'the "Add coordinator" button').toBeDefined();
      add?.click();
      await settle();
      await chooseCandidate('Boris Candidate', settle);

      expect(api.callsFor(addTrackCoordinator)).toEqual([{ id: TRACK_ID, userId: CANDIDATE_ID }]);
      await vi.waitFor(() => expect(snack()).not.toBe(''));
      await vi.waitFor(() => expect(element.textContent).toContain('Quality Engineering'));
    });

    it('TRK-10.1 explains track-archived on listing the candidates and shows the track as it is now', async () => {
      trackChangedMeanwhile(track({ status: 'ARCHIVED' }));
      api.reject(
        listTrackCoordinatorCandidates,
        problem(409, 'urn:mentorhub:problem:track-archived'),
      );
      const { element, settle } = await open();

      const add = buttonByText(element, 'Add coordinator');
      expect(add, 'the "Add coordinator" button').toBeDefined();
      add?.click();
      await settle();

      await vi.waitFor(() => expect(snack()).toMatch(/archived/i));
      await vi.waitFor(() => expect(buttonByText(element, 'Restore')).toBeDefined());
      expect(buttonByText(element, 'Add coordinator')).toBeUndefined();
    });
  });
});
