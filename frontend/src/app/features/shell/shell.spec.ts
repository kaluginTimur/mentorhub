import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Api } from '../../core/api/api';
import { AuthService } from '../../core/auth/auth.service';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { displayName } from '../../core/auth/user-name';
import { ThemePreference, ThemeService } from '../../core/theme/theme.service';
import { Home } from './home';
import { Shell } from './shell';

@Component({ template: `<p>Dummy</p>` })
class DummyPage {}

describe('shell', () => {
  const auth = { signOut: vi.fn() };
  const user = signal({
    id: 'u1',
    email: 'anna@example.com',
    firstName: 'Anna' as string | null,
    lastName: 'Petrova' as string | null,
    roles: ['MENTOR'],
    permissions: [] as string[],
    phone: null,
  });
  const currentUser = {
    user,
    name: () => displayName(user()),
    hasPermission: (p: string) => user().permissions.includes(p),
  };
  const themePreference = signal<ThemePreference>('system');
  const theme = {
    preference: themePreference.asReadonly(),
    setPreference: vi.fn((pref: ThemePreference) => themePreference.set(pref)),
  };
  const api = { invoke: vi.fn() };

  function accountMenuTrigger(element: HTMLElement): HTMLButtonElement | null {
    return element.querySelector('button[aria-label^="Account menu for "]');
  }

  function themeMenuTrigger(element: HTMLElement): HTMLButtonElement | null {
    return element.querySelector('button[aria-label^="Theme: "]');
  }

  beforeEach(() => {
    vi.clearAllMocks();
    themePreference.set('system');
    user.set({
      id: 'u1',
      email: 'anna@example.com',
      firstName: 'Anna',
      lastName: 'Petrova',
      roles: ['MENTOR'],
      permissions: [],
      phone: null,
    });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: '',
            component: Shell,
            children: [
              { path: '', component: DummyPage },
              { path: 'users', component: DummyPage },
              { path: 'invitations', component: DummyPage },
              { path: 'profile', component: DummyPage },
            ],
          },
        ]),
        { provide: AuthService, useValue: auth },
        { provide: CurrentUserService, useValue: currentUser },
        { provide: ThemeService, useValue: theme },
        { provide: Api, useValue: api },
      ],
    });
  });

  it('shows the avatar, email, and signs out from the account menu', async () => {
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const trigger = accountMenuTrigger(element);
    expect(trigger?.querySelector('app-avatar')?.textContent?.trim()).toBe('AP');

    trigger?.click();
    await fixture.whenStable();
    const overlay = document.body.textContent;
    expect(overlay).toContain('anna@example.com');
    const signOut = [...document.querySelectorAll('[hlmDropdownMenuItem]')].find(
      (b) => b.textContent?.trim() === 'Sign out',
    ) as HTMLElement | undefined;
    signOut?.click();
    expect(auth.signOut).toHaveBeenCalledOnce();
  });

  it.each([
    ['both names', 'Anna', 'Petrova', 'Anna Petrova'],
    ['only the first name', 'Anna', null, 'Anna'],
    ['only the last name', null, 'Petrova', 'Petrova'],
    ['the email when no name is set', null, null, 'anna@example.com'],
  ])('IDN-11.1 shows %s in the account menu', async (_case, firstName, lastName, expected) => {
    user.update((u) => ({ ...u, firstName, lastName }));
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    const trigger = accountMenuTrigger(fixture.nativeElement as HTMLElement);
    expect(trigger?.textContent).toContain(expected);
    expect(trigger?.getAttribute('aria-label')).toBe(`Account menu for ${expected}`);
  });

  it('wraps the navigation and brand link in a header landmark', async () => {
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('header nav[aria-label="Primary"]')).not.toBeNull();
    const brand = [...element.querySelectorAll('header a')].find((a) =>
      a.textContent?.includes('MentorHub'),
    );
    expect(brand?.getAttribute('href')).toBe('/');
  });

  it('shows the Users link only with USERS_READ', async () => {
    const link = (element: HTMLElement) =>
      [...element.querySelectorAll('nav[aria-label="Primary"] a')].find(
        (a) => a.textContent?.trim() === 'Users',
      );
    user.update((u) => ({ ...u, permissions: ['USERS_READ'] }));
    let fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(link(fixture.nativeElement as HTMLElement)?.getAttribute('href')).toBe('/users');

    user.update((u) => ({ ...u, permissions: [] }));
    fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(link(fixture.nativeElement as HTMLElement)).toBeUndefined();
  });

  it('shows the Invitations link only with USERS_INVITE', async () => {
    const link = (element: HTMLElement) =>
      [...element.querySelectorAll('nav[aria-label="Primary"] a')].find(
        (a) => a.textContent?.trim() === 'Invitations',
      );
    user.update((u) => ({ ...u, permissions: ['USERS_READ', 'USERS_INVITE'] }));
    let fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(link(fixture.nativeElement as HTMLElement)?.getAttribute('href')).toBe('/invitations');

    user.update((u) => ({ ...u, permissions: ['USERS_READ'] }));
    fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(link(fixture.nativeElement as HTMLElement)).toBeUndefined();
  });

  it('marks the active section link with aria-current="page" and active highlight', async () => {
    user.update((u) => ({ ...u, permissions: ['USERS_READ', 'USERS_INVITE'] }));
    const harness = await RouterTestingHarness.create('/users');
    await harness.fixture.whenStable();
    const element = harness.fixture.nativeElement as HTMLElement;
    const usersLink = [...element.querySelectorAll('nav[aria-label="Primary"] a')].find(
      (a) => a.textContent?.trim() === 'Users',
    );
    const invitationsLink = [...element.querySelectorAll('nav[aria-label="Primary"] a')].find(
      (a) => a.textContent?.trim() === 'Invitations',
    );
    expect(usersLink?.getAttribute('aria-current')).toBe('page');
    expect(usersLink?.className).toContain('after:scale-x-100');
    expect(invitationsLink?.getAttribute('aria-current')).toBeNull();
  });

  it('toggles the mobile navigation menu when links exist', async () => {
    user.update((u) => ({ ...u, permissions: ['USERS_READ'] }));
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const mobileToggle = element.querySelector(
      'button[aria-controls="mobile-nav"]',
    ) as HTMLButtonElement | null;
    expect(mobileToggle?.getAttribute('aria-expanded')).toBe('false');
    expect(element.querySelector('#mobile-nav')).toBeNull();

    mobileToggle?.click();
    await fixture.whenStable();
    expect(mobileToggle?.getAttribute('aria-expanded')).toBe('true');
    const mobileUsersLink = element.querySelector('#mobile-nav a[href="/users"]') as HTMLElement;
    expect(mobileUsersLink).not.toBeNull();

    mobileUsersLink.click();
    await fixture.whenStable();
    expect(element.querySelector('#mobile-nav')).toBeNull();
  });

  it('switches themes via the Theme menu wired to ThemeService', async () => {
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    const trigger = themeMenuTrigger(element);
    expect(trigger?.getAttribute('aria-label')).toBe('Theme: System');

    trigger?.click();
    await fixture.whenStable();
    const darkOption = [...document.querySelectorAll('[hlmDropdownMenuRadio]')].find(
      (item) => item.textContent?.trim() === 'Dark',
    ) as HTMLElement | undefined;
    darkOption?.click();
    await fixture.whenStable();

    expect(theme.setPreference).toHaveBeenCalledWith('dark');
    expect(trigger?.getAttribute('aria-label')).toBe('Theme: Dark');
  });

  it('has Profile above Sign out in the account menu', async () => {
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    accountMenuTrigger(fixture.nativeElement as HTMLElement)?.click();
    await fixture.whenStable();
    const items = [...document.querySelectorAll('[hlmDropdownMenuItem]')].map((i) =>
      i.textContent?.trim(),
    );
    expect(items).toEqual(['Profile', 'Sign out']);
    expect(document.querySelector('a[hlmDropdownMenuItem]')?.getAttribute('href')).toBe('/profile');
  });

  it('greets the user and shows the version on the home page', async () => {
    api.invoke.mockResolvedValue({ name: 'MentorHub', version: '0.1.0' });
    const fixture = TestBed.createComponent(Home);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('h1')).toHaveLength(1);
    expect(element.querySelector('h1')?.textContent?.trim()).toBe('Welcome, Anna');
    expect(element.querySelector('footer')?.textContent).toContain('MentorHub 0.1.0');
  });

  it('hides the version when the system info cannot be loaded', async () => {
    api.invoke.mockRejectedValue(new Error('down'));
    const fixture = TestBed.createComponent(Home);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('h1')?.textContent?.trim()).toBe('Welcome, Anna');
    expect(element.querySelector('footer')?.textContent?.trim()).toBe('');
  });

  it('shows quick-action cards on Home gated by user permissions', async () => {
    api.invoke.mockResolvedValue({ name: 'MentorHub', version: '0.1.0' });
    user.update((u) => ({ ...u, permissions: ['USERS_READ'] }));
    const fixture = TestBed.createComponent(Home);
    await fixture.whenStable();
    let element = fixture.nativeElement as HTMLElement;
    let hrefs = [...element.querySelectorAll('section[aria-label="Quick actions"] a')].map((a) =>
      a.getAttribute('href'),
    );
    expect(hrefs).toEqual(['/users', '/profile']);

    user.update((u) => ({ ...u, permissions: ['USERS_READ', 'USERS_INVITE'] }));
    const fullFixture = TestBed.createComponent(Home);
    await fullFixture.whenStable();
    element = fullFixture.nativeElement as HTMLElement;
    hrefs = [...element.querySelectorAll('section[aria-label="Quick actions"] a')].map((a) =>
      a.getAttribute('href'),
    );
    expect(hrefs).toEqual(['/users', '/invitations', '/profile']);
  });
});
