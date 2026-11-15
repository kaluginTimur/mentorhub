import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { addTrackCoordinator } from '../../core/api/fn/tracks/add-track-coordinator';
import { archiveTrack } from '../../core/api/fn/tracks/archive-track';
import { getTrack } from '../../core/api/fn/tracks/get-track';
import { getTrackHistory } from '../../core/api/fn/tracks/get-track-history';
import { listTrackCoordinatorCandidates } from '../../core/api/fn/tracks/list-track-coordinator-candidates';
import { removeTrackCoordinator } from '../../core/api/fn/tracks/remove-track-coordinator';
import { restoreTrack } from '../../core/api/fn/tracks/restore-track';
import { updateTrack } from '../../core/api/fn/tracks/update-track';
import { MeUser } from '../../core/api/models/me-user';
import { Permission } from '../../core/api/models/permission';
import { Track } from '../../core/api/models/track';
import { TrackHistoryEntry } from '../../core/api/models/track-history-entry';
import { TrackHistoryPage } from '../../core/api/models/track-history-page';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { problemError } from '../../core/testing/problem-error';
import {
  ADMIN_ID,
  CANDIDATE_ID,
  COORDINATOR_ID,
  TRACK_ID,
  candidate,
  coordinator,
  track,
  trackAdmin,
} from './test-data';
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

  async function open(id = TRACK_ID) {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/tracks/${id}`);
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

  describe('details', () => {
    const historyPage = (
      items: TrackHistoryEntry[],
      totalItems = items.length,
    ): TrackHistoryPage => ({
      items,
      page: 0,
      size: 25,
      totalItems,
    });
    const entry = (id: string, extra: Partial<TrackHistoryEntry> = {}): TrackHistoryEntry => ({
      id,
      action: 'TRACK_ARCHIVED',
      occurredAt: '2026-11-15T10:00:00Z',
      actor: { id: ADMIN_ID, name: 'Anna Petrova' },
      ...extra,
    });
    const heading = (element: HTMLElement) => element.querySelector('h1')?.textContent?.trim();
    const inputLabelled = (root: HTMLElement, text: string) => {
      const id = [...root.querySelectorAll('label')]
        .find((l) => l.textContent?.trim() === text)
        ?.getAttribute('for');
      return root.querySelector<HTMLInputElement>(`#${id}`);
    };
    // The server's track: what the last change left, as every later load returns it.
    const serverTrack = (initial: Track) => {
      let current = initial;
      api.handle(getTrack, () => current);
      return (next: Track) => {
        current = next;
        return next;
      };
    };

    it('shows the code, name, status, coordinators with their marks and the history', async () => {
      api.resolve(
        getTrack,
        track({
          coordinators: [
            coordinator(),
            coordinator({
              id: 'c2',
              name: 'Boris Disabled',
              email: 'boris@example.com',
              disabled: true,
            }),
            coordinator({
              id: 'c3',
              name: 'Daniel Mentor',
              email: 'daniel@example.com',
              coordinatorRole: false,
            }),
          ],
        }),
      );
      api.resolve(
        getTrackHistory,
        historyPage([
          entry('h1', {
            action: 'TRACK_COORDINATOR_ADDED',
            user: { id: COORDINATOR_ID, name: 'Clara Coordinator' },
          }),
          entry('h2', {
            action: 'TRACK_UPDATED',
            changes: [{ field: 'name', oldValue: 'Testing', newValue: 'Quality Assurance' }],
          }),
          entry('h3', { action: 'TRACK_CREATED', code: 'QA', name: 'Testing', actor: null }),
        ]),
      );
      const { element } = await open();

      expect(element.querySelectorAll('h1')).toHaveLength(1);
      expect(element.textContent).toContain('Quality Assurance');
      expect(element.textContent).toContain('QA');
      expect(element.textContent).toContain('Active');
      // A coordinator's row is the one that shows their email.
      const rowOf = (email: string) =>
        [...element.querySelectorAll('li')].find((li) => li.textContent?.includes(email));
      expect(rowOf('clara@example.com')?.textContent).toContain('Clara Coordinator');
      expect(rowOf('clara@example.com')?.textContent).not.toMatch(/Disabled|No coordinator role/);
      expect(rowOf('boris@example.com')?.textContent).toContain('Boris Disabled');
      expect(rowOf('boris@example.com')?.textContent).toContain('Disabled');
      expect(rowOf('boris@example.com')?.textContent).not.toContain('No coordinator role');
      expect(rowOf('daniel@example.com')?.textContent).toContain('Daniel Mentor');
      expect(rowOf('daniel@example.com')?.textContent).toContain('No coordinator role');
      expect(rowOf('daniel@example.com')?.textContent).not.toContain('Disabled');
      const history = element.querySelector('.history') as HTMLElement;
      const entries = [...history.querySelectorAll('li')];
      expect(entries).toHaveLength(3);
      expect(entries[0].textContent).toContain('Anna Petrova');
      expect(entries[0].textContent).toContain('Clara Coordinator');
      expect(entries[1].textContent).toContain('Testing');
      expect(entries[1].textContent).toContain('Quality Assurance');
      expect(entries[2].textContent).toContain('System');
      expect(element.querySelector('.back')?.getAttribute('href')).toBe('/tracks');
    });

    it('does not explain marked assignments when no coordinator is marked', async () => {
      const hint = /grants nothing/i;
      api.resolve(getTrack, track());
      const unmarked = await open();
      expect(unmarked.element.textContent).not.toMatch(hint);
    });

    it('explains marked assignments when a coordinator is marked', async () => {
      api.resolve(getTrack, track({ coordinators: [coordinator({ disabled: true })] }));
      const { element } = await open();
      expect(element.textContent).toMatch(/grants nothing/i);
    });

    it.each<[string, Track['status']]>([
      ['an active', 'ACTIVE'],
      ['an archived', 'ARCHIVED'],
    ])('shows %s track read-only without TRACKS_MANAGE', async (_case, status) => {
      me.set(trackAdmin({ permissions: ['TRACKS_READ'] }));
      api.resolve(getTrack, track({ status }));
      const { element } = await open();
      for (const text of ['Edit', 'Archive', 'Restore', 'Add coordinator', 'Remove']) {
        expect(buttonByText(element, text), text).toBeUndefined();
      }
      expect(element.textContent).toContain('Clara Coordinator');
    });

    it('offers only Restore on an archived track and says it is read-only', async () => {
      api.resolve(getTrack, track({ status: 'ARCHIVED' }));
      const { element } = await open();
      expect(buttonByText(element, 'Restore')).toBeDefined();
      for (const text of ['Edit', 'Archive', 'Add coordinator', 'Remove']) {
        expect(buttonByText(element, text), text).toBeUndefined();
      }
      expect(element.textContent).toContain('Clara Coordinator');
      expect(element.textContent).toMatch(/archived/i);
    });

    it.each([
      ['an unknown', TRACK_ID, 404, 'urn:mentorhub:problem:not-found'],
      ['an invalid', 'abc', 400, 'urn:mentorhub:problem:validation-failed'],
    ])(
      'says the track is not found for %s id and links back to the list',
      async (_case, id, status, type) => {
        api.reject(getTrack, problem(status, type));
        api.reject(getTrackHistory, problem(status, type));
        const { element } = await open(id);
        await vi.waitFor(() => expect(heading(element)).toBe('Track not found'));
        expect(element.querySelector('a[href="/tracks"]')).not.toBeNull();
      },
    );

    it('says MentorHub is unavailable when the track fails to load, and Retry loads it', async () => {
      api.rejectOnce(getTrack, new Error('down'));
      api.resolve(getTrack, track());
      const { element, settle } = await open();
      expect(element.querySelector('[role=alert]')?.textContent).toContain('unavailable');
      buttonByText(element, 'Retry')?.click();
      await settle();
      expect(heading(element)).toBe('Quality Assurance');
    });

    it('takes the user home when the API refuses the track', async () => {
      api.reject(getTrack, problem(403, 'urn:mentorhub:problem:forbidden'));
      api.reject(getTrackHistory, problem(403, 'urn:mentorhub:problem:forbidden'));
      await open();
      await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/'));
    });

    it.each<[string, Permission[], boolean]>([
      ['with USERS_READ', ['TRACKS_READ', 'USERS_READ'], true],
      ['not without USERS_READ', ['TRACKS_READ'], false],
    ])('links a coordinator to their user page %s', async (_case, permissions, linked) => {
      me.set(trackAdmin({ permissions }));
      api.resolve(getTrack, track());
      const { element } = await open();
      expect(element.querySelector(`a[href="/users/${COORDINATOR_ID}"]`) !== null).toBe(linked);
      expect(element.textContent).toContain('Clara Coordinator');
    });

    describe('history', () => {
      it('loads more on "Show more"', async () => {
        api.handle(getTrackHistory, (p) =>
          p.page === 1 ? historyPage([entry('h3')], 3) : historyPage([entry('h1'), entry('h2')], 3),
        );
        api.resolve(getTrack, track());
        const { element, settle } = await open();
        expect(element.querySelectorAll('.history li')).toHaveLength(2);
        buttonByText(element, 'Show more')?.click();
        await settle();
        expect(element.querySelectorAll('.history li')).toHaveLength(3);
        expect(buttonByText(element, 'Show more')).toBeUndefined();
      });

      it('says when it failed and retries it, keeping the track', async () => {
        api.resolve(getTrack, track());
        api.rejectOnce(getTrackHistory, new Error('down'));
        const { element, settle } = await open();
        expect(element.textContent).toMatch(/could not load the history/i);
        expect(heading(element)).toBe('Quality Assurance');
        api.resolve(getTrackHistory, historyPage([entry('h1')]));
        buttonByText(element, 'Retry')?.click();
        await settle();
        expect(element.querySelectorAll('.history li')).toHaveLength(1);
      });
    });

    describe('editing', () => {
      async function openDialog() {
        const page = await open();
        buttonByText(page.element, 'Edit')?.click();
        await page.settle();
        expect(inputLabelled(overlay(), 'Code'), 'the "Code" field').not.toBeNull();
        return page;
      }
      const submit = () =>
        overlay().querySelector<HTMLButtonElement>('button[type=submit]')?.click();

      it('starts from the stored values, saves a changed name and shows it', async () => {
        const store = serverTrack(track());
        api.handle(updateTrack, (p) => store(track({ ...p.body })));
        const { element, settle } = await openDialog();
        expect(inputLabelled(overlay(), 'Code')?.value).toBe('QA');
        expect(inputLabelled(overlay(), 'Name')?.value).toBe('Quality Assurance');

        const name = inputLabelled(overlay(), 'Name') as HTMLInputElement;
        name.value = ' Quality Engineering ';
        name.dispatchEvent(new Event('input'));
        await settle();
        submit();
        await settle();

        expect(api.callsFor(updateTrack)).toEqual([
          { id: TRACK_ID, body: { code: 'QA', name: 'Quality Engineering' } },
        ]);
        await vi.waitFor(() => expect(heading(element)).toBe('Quality Engineering'));
        expect(inputLabelled(overlay(), 'Code')).toBeNull();
      });

      it('explains track-archived and shows the track as it is now', async () => {
        trackChangedMeanwhile(track({ status: 'ARCHIVED' }));
        api.reject(updateTrack, problem(409, 'urn:mentorhub:problem:track-archived'));
        const { element, settle } = await openDialog();

        const name = inputLabelled(overlay(), 'Name') as HTMLInputElement;
        name.value = 'Quality Engineering';
        name.dispatchEvent(new Event('input'));
        await settle();
        submit();
        await settle();

        await vi.waitFor(() => expect(snack()).toMatch(/archived/i));
        await vi.waitFor(() => expect(buttonByText(element, 'Restore')).toBeDefined());
        expect(buttonByText(element, 'Edit')).toBeUndefined();
      });

      it('does not send a blank name', async () => {
        api.resolve(getTrack, track());
        const { settle } = await openDialog();
        const name = inputLabelled(overlay(), 'Name') as HTMLInputElement;
        name.value = '   ';
        name.dispatchEvent(new Event('input'));
        await settle();
        submit();
        await settle();
        expect(api.callsFor(updateTrack)).toHaveLength(0);
        expect(overlay().querySelector('[role=alert]')).not.toBeNull();
      });
    });

    describe('archiving and restoring', () => {
      it('archives after a confirmation', async () => {
        const store = serverTrack(track());
        api.handle(archiveTrack, () => store(track({ status: 'ARCHIVED' })));
        const { element, settle } = await open();

        buttonByText(element, 'Archive')?.click();
        await settle();
        expect(api.callsFor(archiveTrack)).toHaveLength(0);
        expect(overlay().textContent).toContain('Quality Assurance');
        buttonByText(overlay(), 'Archive')?.click();
        await settle();

        await vi.waitFor(() => expect(api.callsFor(archiveTrack)).toEqual([{ id: TRACK_ID }]));
        await vi.waitFor(() => expect(buttonByText(element, 'Restore')).toBeDefined());
        expect(buttonByText(element, 'Archive')).toBeUndefined();
        expect(element.textContent).toContain('Clara Coordinator');
      });

      it('does not archive when the confirmation is cancelled', async () => {
        api.resolve(getTrack, track());
        const { element, settle } = await open();
        buttonByText(element, 'Archive')?.click();
        await settle();
        buttonByText(overlay(), 'Cancel')?.click();
        await settle();
        expect(api.callsFor(archiveTrack)).toHaveLength(0);
        expect(buttonByText(element, 'Archive')).toBeDefined();
      });

      it('restores after a confirmation', async () => {
        const store = serverTrack(track({ status: 'ARCHIVED' }));
        api.handle(restoreTrack, () => store(track()));
        const { element, settle } = await open();

        buttonByText(element, 'Restore')?.click();
        await settle();
        expect(api.callsFor(restoreTrack)).toHaveLength(0);
        buttonByText(overlay(), 'Restore')?.click();
        await settle();

        await vi.waitFor(() => expect(api.callsFor(restoreTrack)).toEqual([{ id: TRACK_ID }]));
        await vi.waitFor(() => expect(buttonByText(element, 'Edit')).toBeDefined());
        expect(buttonByText(element, 'Archive')).toBeDefined();
        expect(buttonByText(element, 'Restore')).toBeUndefined();
      });

      it('does not restore when the confirmation is cancelled', async () => {
        api.resolve(getTrack, track({ status: 'ARCHIVED' }));
        const { element, settle } = await open();
        buttonByText(element, 'Restore')?.click();
        await settle();
        buttonByText(overlay(), 'Cancel')?.click();
        await settle();
        expect(api.callsFor(restoreTrack)).toHaveLength(0);
      });
    });

    describe('coordinators', () => {
      it('names a chosen candidate and shows them', async () => {
        const store = serverTrack(track());
        const named = coordinator({
          id: CANDIDATE_ID,
          name: 'Boris Candidate',
          email: 'boris@example.com',
        });
        api.handle(addTrackCoordinator, () =>
          store(track({ coordinators: [named, coordinator()] })),
        );
        const { element, settle } = await open();

        buttonByText(element, 'Add coordinator')?.click();
        await settle();
        await chooseCandidate('Boris Candidate', settle);

        expect(api.callsFor(addTrackCoordinator)).toEqual([{ id: TRACK_ID, userId: CANDIDATE_ID }]);
        await vi.waitFor(() => expect(element.textContent).toContain('Boris Candidate'));
        expect(element.textContent).toContain('Clara Coordinator');
      });

      it('says when there is no candidate and names nobody', async () => {
        api.resolve(getTrack, track());
        api.resolve(listTrackCoordinatorCandidates, { items: [] });
        const { element, settle } = await open();

        buttonByText(element, 'Add coordinator')?.click();
        await settle();
        expect(overlay().textContent).toMatch(/no .*coordinators? .*available/i);
        buttonByText(overlay(), 'Add')?.click();
        await settle();

        expect(api.callsFor(addTrackCoordinator)).toHaveLength(0);
      });

      it('names nobody until a candidate is chosen', async () => {
        api.resolve(getTrack, track());
        const { element, settle } = await open();

        buttonByText(element, 'Add coordinator')?.click();
        await settle();
        buttonByText(overlay(), 'Add')?.click();
        await settle();

        expect(api.callsFor(addTrackCoordinator)).toHaveLength(0);
        expect(overlay().querySelector('[role=alert]')).not.toBeNull();
      });

      it('removes a coordinator without a confirmation', async () => {
        const store = serverTrack(track());
        api.handle(removeTrackCoordinator, () => store(track({ coordinators: [] })));
        const { element, settle } = await open();

        buttonByText(element, 'Remove')?.click();
        await settle();

        expect(api.callsFor(removeTrackCoordinator)).toEqual([
          { id: TRACK_ID, userId: COORDINATOR_ID },
        ]);
        await vi.waitFor(() => expect(element.textContent).not.toContain('Clara Coordinator'));
      });
    });
  });
});
