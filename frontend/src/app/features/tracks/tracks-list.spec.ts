import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { createTrack } from '../../core/api/fn/tracks/create-track';
import { getTrack } from '../../core/api/fn/tracks/get-track';
import { getTrackHistory } from '../../core/api/fn/tracks/get-track-history';
import { listTracks } from '../../core/api/fn/tracks/list-tracks';
import { MeUser } from '../../core/api/models/me-user';
import { Permission } from '../../core/api/models/permission';
import { Track } from '../../core/api/models/track';
import { TrackList } from '../../core/api/models/track-list';
import { ValidationError } from '../../core/api/models/validation-error';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { createApiStub, provideApiStub } from '../../core/testing/api-stub';
import { problemError } from '../../core/testing/problem-error';
import { TRACK_ID, coordinator, track, trackAdmin } from './test-data';
import { TrackPage } from './track-page';
import { TracksList } from './tracks-list';

const list = (...items: Track[]): TrackList => ({ items });
const buttonByText = (root: ParentNode, text: string) =>
  [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === text);
const validationProblem = (errors: ValidationError[]) =>
  problemError({
    type: 'urn:mentorhub:problem:validation-failed',
    status: 400,
    title: 'Validation Failed',
    errors,
  });

describe('TracksList', () => {
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
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'tracks', component: TracksList },
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

  async function open(url = '/tracks') {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
    harness.detectChanges();
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
      await harness.fixture.whenStable();
    };
    return { harness, element: () => harness.routeNativeElement as HTMLElement, settle };
  }

  const router = () => TestBed.inject(Router);
  const query = () => router().parseUrl(router().url).queryParams;
  const requested = () => api.callsFor(listTracks).at(-1)?.status ?? 'ACTIVE';
  const overlay = () => document.querySelector('.cdk-overlay-container') as HTMLElement;
  const snack = () => document.querySelector('app-toast-host')?.textContent?.trim() ?? '';
  const trackLink = (element: HTMLElement, id: string) =>
    element.querySelector<HTMLAnchorElement>(`a[href="/tracks/${id}"]`);
  // The item of the list that holds the link to the track.
  const itemOf = (element: HTMLElement, id: string) => trackLink(element, id)?.closest('li');
  const inputLabelled = (root: HTMLElement, text: string) => {
    const id = [...root.querySelectorAll('label')]
      .find((l) => l.textContent?.trim() === text)
      ?.getAttribute('for');
    return root.querySelector<HTMLInputElement>(`#${id}`);
  };
  const type = (input: HTMLInputElement | null, value: string) => {
    expect(input).not.toBeNull();
    (input as HTMLInputElement).value = value;
    input?.dispatchEvent(new Event('input'));
  };

  async function chooseStatus(element: HTMLElement, option: string, settle: () => Promise<void>) {
    const triggerId = [...element.querySelectorAll('label')]
      .find((l) => l.textContent?.trim() === 'Status')
      ?.getAttribute('for');
    expect(triggerId, 'the "Status" filter').toBeTruthy();
    element.querySelector<HTMLElement>(`#${triggerId}`)?.click();
    await settle();
    const choice = [...document.querySelectorAll<HTMLElement>('hlm-select-item')].find(
      (o) => o.textContent?.trim() === option,
    );
    expect(choice, `the "${option}" option`).toBeDefined();
    choice?.click();
    await settle();
  }

  it('shows each track with its code, name, status and coordinators with their marks, linking to the track page', async () => {
    const devops = track({
      id: 'devops',
      code: 'DEVOPS',
      name: 'DevOps',
      coordinators: [
        coordinator({ id: 'c1', name: 'Clara Coordinator' }),
        coordinator({ id: 'c2', name: 'Boris Disabled', disabled: true }),
        coordinator({ id: 'c3', name: 'Daniel Mentor', coordinatorRole: false }),
      ],
    });
    const qa = track({ id: 'qa', coordinators: [] });
    api.resolve(listTracks, list(devops, qa));
    const { element } = await open();

    expect(element().querySelector('h1')?.textContent?.trim()).toBe('Tracks');
    const devopsItem = itemOf(element(), 'devops') as HTMLElement;
    expect(devopsItem.textContent).toContain('DEVOPS');
    expect(trackLink(element(), 'devops')?.textContent?.trim()).toBe('DevOps');
    expect(devopsItem.textContent).toContain('Active');
    expect(devopsItem.textContent).toContain('Clara Coordinator');
    const rowOf = (name: string) =>
      [...devopsItem.querySelectorAll('li')].find((li) => li.textContent?.includes(name));
    expect(rowOf('Clara Coordinator')?.textContent).not.toMatch(/Disabled|No coordinator role/);
    expect(rowOf('Boris Disabled')?.textContent).toContain('Disabled');
    expect(rowOf('Boris Disabled')?.textContent).not.toContain('No coordinator role');
    expect(rowOf('Daniel Mentor')?.textContent).toContain('No coordinator role');
    expect(itemOf(element(), 'qa')?.textContent).toContain('Quality Assurance');
  });

  it('lists the active tracks by default', async () => {
    api.resolve(listTracks, list(track()));
    await open();
    expect(requested()).toBe('ACTIVE');
  });

  it('restores the status filter from the address, as on a reload', async () => {
    api.resolve(listTracks, list(track({ status: 'ARCHIVED' })));
    const { element } = await open('/tracks?status=ARCHIVED');
    expect(requested()).toBe('ARCHIVED');
    const triggerId = [...element().querySelectorAll('label')]
      .find((l) => l.textContent?.trim() === 'Status')
      ?.getAttribute('for');
    expect(element().querySelector(`#${triggerId}`)?.textContent).toContain('Archived');
  });

  it('ignores an unknown status in the address', async () => {
    api.resolve(listTracks, list(track()));
    await open('/tracks?status=DELETED');
    expect(requested()).toBe('ACTIVE');
  });

  it.each([
    ['All', 'ALL'],
    ['Archived', 'ARCHIVED'],
  ])('writes the %s filter to the address and lists those tracks', async (option, status) => {
    api.resolve(listTracks, list(track()));
    const { element, settle } = await open();
    await chooseStatus(element(), option, settle);
    expect(query()).toEqual({ status });
    expect(requested()).toBe(status);
  });

  it('says that no track exists yet when the list of all tracks is empty', async () => {
    api.resolve(listTracks, list());
    const { element } = await open('/tracks?status=ALL');
    expect(element().textContent).toMatch(/no tracks yet/i);
  });

  it('says that no track has the chosen status when the archived list is empty', async () => {
    api.resolve(listTracks, list());
    const { element } = await open('/tracks?status=ARCHIVED');
    expect(element().textContent).toMatch(/no archived tracks/i);
    expect(element().textContent).not.toMatch(/no tracks yet/i);
  });

  it('PLT-9.1 says MentorHub is unavailable when the list fails, and Retry loads it again', async () => {
    api.rejectOnce(listTracks, new Error('down'));
    const { element, settle } = await open();
    expect(element().querySelector('[role=alert]')?.textContent).toContain(
      'MentorHub is unavailable',
    );
    api.resolve(listTracks, list(track()));
    buttonByText(element(), 'Retry')?.click();
    await settle();
    expect(trackLink(element(), TRACK_ID)).not.toBeNull();
  });

  it('PLT-9.3 takes the user home when the API refuses the list with 403', async () => {
    api.reject(
      listTracks,
      problemError({ type: 'urn:mentorhub:problem:forbidden', title: 'Forbidden', status: 403 }),
    );
    await open();
    await vi.waitFor(() => expect(router().url).toBe('/'));
  });

  it.each<[string, Permission[], boolean]>([
    ['with TRACKS_MANAGE', ['TRACKS_READ', 'TRACKS_MANAGE'], true],
    ['not without TRACKS_MANAGE', ['TRACKS_READ'], false],
  ])('offers "Create track" %s', async (_case, permissions, offered) => {
    api.resolve(listTracks, list(track()));
    me.set(trackAdmin({ permissions }));
    const { element } = await open();
    expect(buttonByText(element(), 'Create track') !== undefined).toBe(offered);
  });

  describe('creating a track', () => {
    async function openDialog() {
      const page = await open();
      buttonByText(page.element(), 'Create track')?.click();
      await page.settle();
      expect(inputLabelled(overlay(), 'Code'), 'the "Code" field').not.toBeNull();
      return page;
    }
    const submit = () => overlay().querySelector<HTMLButtonElement>('button[type=submit]')?.click();

    it('sends the trimmed code and name and shows the new track', async () => {
      const created = track({ id: 'new', code: 'T1', name: 'Test', coordinators: [] });
      let stored: Track[] = [];
      api.handle(listTracks, () => list(...stored));
      api.handle(createTrack, () => {
        stored = [created];
        return created;
      });
      const { element, settle } = await openDialog();

      type(inputLabelled(overlay(), 'Code'), '  T1 ');
      type(inputLabelled(overlay(), 'Name'), ' Test  ');
      await settle();
      submit();
      await settle();

      expect(api.callsFor(createTrack)).toEqual([{ body: { code: 'T1', name: 'Test' } }]);
      await vi.waitFor(() => expect(inputLabelled(overlay(), 'Code')).toBeNull());
      await settle();
      await vi.waitFor(() =>
        expect(router().url === '/tracks/new' || trackLink(element(), 'new') !== null).toBe(true),
      );
    });

    it.each([
      ['a blank code', '   ', 'Test'],
      ['a blank name', 'T1', '  '],
      ['a code over 20 characters', 'C'.repeat(21), 'Test'],
      ['a name over 100 characters', 'T1', 'N'.repeat(101)],
    ])('does not send %s and explains it in the dialog', async (_case, code, name) => {
      api.resolve(listTracks, list());
      const { settle } = await openDialog();

      type(inputLabelled(overlay(), 'Code'), code);
      type(inputLabelled(overlay(), 'Name'), name);
      await settle();
      submit();
      await settle();

      expect(api.callsFor(createTrack)).toHaveLength(0);
      expect(overlay().querySelector('[role=alert]')).not.toBeNull();
    });

    it('accepts a code of 20 and a name of 100 characters after trimming', async () => {
      api.resolve(listTracks, list());
      api.resolve(createTrack, track({ id: 'new' }));
      const { settle } = await openDialog();

      type(inputLabelled(overlay(), 'Code'), ` ${'C'.repeat(20)} `);
      type(inputLabelled(overlay(), 'Name'), ` ${'N'.repeat(100)} `);
      await settle();
      submit();
      await settle();

      expect(api.callsFor(createTrack)).toEqual([
        { body: { code: 'C'.repeat(20), name: 'N'.repeat(100) } },
      ]);
    });

    it('shows the server field errors under the fields and keeps the dialog open', async () => {
      api.resolve(listTracks, list());
      api.reject(createTrack, validationProblem([{ field: 'code', message: 'Code is too long' }]));
      const { settle } = await openDialog();

      type(inputLabelled(overlay(), 'Code'), 'T1');
      type(inputLabelled(overlay(), 'Name'), 'Test');
      await settle();
      submit();
      await settle();

      expect(overlay().textContent).toContain('Code is too long');
      expect(inputLabelled(overlay(), 'Code')?.getAttribute('aria-invalid')).toBe('true');
    });

    it('sends nothing when cancelled', async () => {
      api.resolve(listTracks, list());
      const { settle } = await openDialog();

      type(inputLabelled(overlay(), 'Code'), 'T1');
      type(inputLabelled(overlay(), 'Name'), 'Test');
      buttonByText(overlay(), 'Cancel')?.click();
      await settle();

      expect(inputLabelled(overlay(), 'Code')).toBeNull();
      expect(api.callsFor(createTrack)).toHaveLength(0);
    });

    it('explains a failed save in a snackbar and keeps the dialog open', async () => {
      api.resolve(listTracks, list());
      api.reject(createTrack, new Error('down'));
      const { settle } = await openDialog();

      type(inputLabelled(overlay(), 'Code'), 'T1');
      type(inputLabelled(overlay(), 'Name'), 'Test');
      await settle();
      submit();
      await settle();

      await vi.waitFor(() => expect(snack()).not.toBe(''));
      expect(inputLabelled(overlay(), 'Code')?.value).toBe('T1');
    });
  });

  it('leads to a track and back to the list with its filter', async () => {
    api.resolve(listTracks, list(track({ status: 'ARCHIVED' })));
    api.resolve(getTrack, track({ status: 'ARCHIVED' }));
    api.resolve(getTrackHistory, { items: [], page: 0, size: 25, totalItems: 0 });
    const { element, settle } = await open('/tracks?status=ARCHIVED');

    trackLink(element(), TRACK_ID)?.click();
    await settle();
    await settle();
    expect(router().url).toBe(`/tracks/${TRACK_ID}`);
    const back = [...element().querySelectorAll<HTMLAnchorElement>('a')].find(
      (a) =>
        a.getAttribute('href')?.startsWith('/tracks') &&
        !a.getAttribute('href')?.includes(TRACK_ID),
    );
    expect(back, 'the back link to the list').toBeDefined();
    back?.click();
    await settle();

    expect(router().url.startsWith('/tracks?')).toBe(true);
    expect(query()).toEqual({ status: 'ARCHIVED' });
  });
});
