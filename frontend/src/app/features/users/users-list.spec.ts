import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Api } from '../../core/api/api';
import { listUsers } from '../../core/api/fn/users/list-users';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { MatDialog } from '@angular/material/dialog';
import { UsersList } from './users-list';

const page = (items: unknown[], totalItems = items.length) => ({
  items,
  page: 0,
  size: 25,
  totalItems,
});
const summary = (id: string, over: object = {}) => ({
  id,
  email: `${id}@example.com`,
  firstName: null,
  lastName: null,
  status: 'ACTIVE',
  roles: [],
  ...over,
});

const click = (root: HTMLElement, text: string) =>
  [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === text)?.click();

describe('UsersList', () => {
  const api = { invoke: vi.fn() };

  beforeEach(() => {
    api.invoke.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'users', component: UsersList }]),
        { provide: Api, useValue: api },
      ],
    });
  });

  async function open(url = '/users') {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
    harness.detectChanges();
    return { harness, element: harness.routeNativeElement as HTMLElement };
  }

  const params = () => api.invoke.mock.calls.at(-1)?.[1] as Record<string, unknown>;

  it('shows the users with roles and status (AC-1, AC-22)', async () => {
    api.invoke.mockResolvedValue(
      page([
        summary('a', { firstName: 'Anna', lastName: 'Petrova', roles: ['ADMIN', 'MENTOR'] }),
        summary('b', { status: 'DISABLED' }),
      ]),
    );
    const { element } = await open();
    expect(element.querySelectorAll('h1')).toHaveLength(1);
    expect(element.querySelector('h1')?.textContent).toBe('Users');
    const rows = element.querySelectorAll('tr[mat-row], tr.mat-mdc-row');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Anna Petrova');
    expect(rows[0].textContent).toContain('Administrator');
    expect(rows[0].textContent).toContain('Mentor');
    expect(rows[0].querySelector('a')?.getAttribute('href')).toBe('/users/a');
    expect(rows[1].textContent).toContain('b@example.com');
    expect(rows[1].textContent).toContain('No roles');
    expect(rows[1].textContent).toContain('Disabled');
    expect(element.querySelectorAll('th')).toHaveLength(4);
    expect(api.invoke).toHaveBeenCalledWith(
      listUsers,
      expect.objectContaining({ page: 0, size: 25 }),
    );
  });

  it('restores search, filters and page from the URL (AC-23)', async () => {
    api.invoke.mockResolvedValue(page([summary('a')], 60));
    const { element } = await open('/users?q=ann&status=DISABLED&role=NONE&page=1');
    expect(params()).toEqual({ q: 'ann', status: 'DISABLED', role: 'NONE', page: 1, size: 25 });
    expect((element.querySelector('input') as HTMLInputElement).value).toBe('ann');
  });

  it('ignores invalid values in the URL', async () => {
    api.invoke.mockResolvedValue(page([summary('a')]));
    await open('/users?status=BOGUS&role=BOGUS&page=-3');
    expect(params()).toEqual({
      q: undefined,
      status: undefined,
      role: undefined,
      page: 0,
      size: 25,
    });
  });

  it('writes the search to the URL 300 ms after typing stops (AC-23)', async () => {
    api.invoke.mockResolvedValue(page([summary('a')]));
    const { harness, element } = await open('/users?page=2');
    const input = element.querySelector('input') as HTMLInputElement;
    input.value = 'iv';
    input.dispatchEvent(new Event('input'));
    input.value = 'ivan';
    input.dispatchEvent(new Event('input'));
    expect(TestBed.inject(Router).url).toBe('/users?page=2');
    await new Promise((resolve) => setTimeout(resolve, 400));
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/users?q=ivan');
    expect(params()).toEqual(expect.objectContaining({ q: 'ivan', page: 0 }));
  });

  it('shows the empty state without filters (AC-23)', async () => {
    api.invoke.mockResolvedValue(page([]));
    const { element } = await open();
    expect(element.textContent).toContain('No users yet.');
    expect(element.textContent).not.toContain('Clear filters');
  });

  it('shows the empty state with filters and clears them (AC-23)', async () => {
    api.invoke.mockResolvedValue(page([]));
    const { harness, element } = await open('/users?q=zzz&status=ACTIVE');
    expect(element.textContent).toContain('No users match the filters.');
    const clear = [...element.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === 'Clear filters',
    );
    clear?.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/users');
    expect(params()).toEqual(expect.objectContaining({ q: undefined, status: undefined }));
  });

  it('shows the progress bar and keeps the rows while loading (AC-23)', async () => {
    api.invoke.mockResolvedValueOnce(page([summary('a')]));
    const { harness, element } = await open();
    let release!: (value: unknown) => void;
    api.invoke.mockReturnValueOnce(new Promise((resolve) => (release = resolve)));
    await TestBed.inject(Router).navigateByUrl('/users?status=ACTIVE');
    harness.detectChanges();
    expect(element.querySelector('mat-progress-bar')?.getAttribute('aria-label')).toBe(
      'Loading users',
    );
    expect(element.querySelector('main')?.getAttribute('aria-busy')).toBe('true');
    expect(element.querySelectorAll('tr.mat-mdc-row')).toHaveLength(1);
    release(page([summary('b'), summary('c')]));
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(element.querySelector('mat-progress-bar')).toBeNull();
    expect(element.querySelectorAll('tr.mat-mdc-row')).toHaveLength(2);
  });

  it('shows the unavailable card and retries (AC-23)', async () => {
    api.invoke.mockRejectedValueOnce(new Error('down'));
    const { harness, element } = await open();
    expect(element.querySelector('[role=alert]')?.textContent).toContain(
      'MentorHub is unavailable',
    );
    api.invoke.mockResolvedValueOnce(page([summary('a')]));
    (element.querySelector('button') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(element.querySelectorAll('tr.mat-mdc-row')).toHaveLength(1);
  });

  it('keeps the paginator when the page is past the last one', async () => {
    api.invoke.mockResolvedValue(page([], 30));
    const { element } = await open('/users?page=5');
    expect(element.querySelector('mat-paginator')).not.toBeNull();
    expect(element.textContent).toContain('No users on this page.');
  });

  it('keeps a trailing space while typing the search', async () => {
    api.invoke.mockResolvedValue(page([summary('a')]));
    const { harness, element } = await open();
    const input = element.querySelector('input') as HTMLInputElement;
    input.value = 'Anna ';
    input.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 400));
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(input.value).toBe('Anna ');
    expect(params()).toEqual(expect.objectContaining({ q: 'Anna' }));
  });

  it('keeps characters typed while the debounced navigation is applied', async () => {
    api.invoke.mockResolvedValue(page([summary('a')]));
    const { harness, element } = await open();
    const input = element.querySelector('input') as HTMLInputElement;
    const router = TestBed.inject(Router);
    const navigate = router.navigate.bind(router);
    let typed = false;
    vi.spyOn(router, 'navigate').mockImplementation((...args) => {
      const result = navigate(...args);
      if (typed) return result;
      typed = true;
      // Typed after the debounced navigation started, before it is applied.
      input.value = 'ab';
      input.dispatchEvent(new Event('input'));
      harness.detectChanges();
      return result;
    });
    input.value = 'a';
    input.dispatchEvent(new Event('input'));
    await vi.waitFor(() => expect(router.url).toBe('/users?q=a'));
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(input.value).toBe('ab');
    await new Promise((resolve) => setTimeout(resolve, 400));
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/users?q=ab');
  });

  it('syncs the input with the URL on Back', async () => {
    api.invoke.mockResolvedValue(page([summary('a')]));
    const { harness, element } = await open('/users?q=one');
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/users?q=two');
    harness.detectChanges();
    expect((element.querySelector('input') as HTMLInputElement).value).toBe('two');
  });

  it('cancels a pending search when the filters are cleared', async () => {
    api.invoke.mockResolvedValue(page([]));
    const { harness, element } = await open('/users?status=ACTIVE');
    const input = element.querySelector('input') as HTMLInputElement;
    input.value = 'late';
    input.dispatchEvent(new Event('input'));
    click(element, 'Clear filters');
    await new Promise((resolve) => setTimeout(resolve, 400));
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/users');
  });

  it('sends the user home when the API refuses the list with 403', async () => {
    api.invoke.mockRejectedValue(
      new HttpErrorResponse({ status: 403, error: { type: 'urn:mentorhub:problem:forbidden' } }),
    );
    const { harness } = await open();
    await harness.fixture.whenStable();
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/'));
  });

  describe('Invite button (AC-18)', () => {
    const inviteButton = (element: HTMLElement) =>
      [...element.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Invite');

    afterEach(() => TestBed.inject(MatDialog).closeAll());

    it('is shown next to the heading with USERS_INVITE and opens the invite dialog', async () => {
      TestBed.overrideProvider(CurrentUserService, {
        useValue: { hasPermission: (p: string) => p === 'USERS_INVITE' },
      });
      api.invoke.mockResolvedValue(page([summary('a')]));
      const { element } = await open();
      expect(element.querySelector('.heading h1')?.textContent).toBe('Users');
      inviteButton(element)?.click();
      await vi.waitFor(() =>
        expect(document.querySelector('mat-dialog-container')?.textContent).toContain(
          'Invite a person',
        ),
      );
    });

    it('is not rendered without USERS_INVITE', async () => {
      TestBed.overrideProvider(CurrentUserService, {
        useValue: { hasPermission: () => false },
      });
      api.invoke.mockResolvedValue(page([summary('a')]));
      const { element } = await open();
      expect(inviteButton(element)).toBeUndefined();
    });
  });
});
