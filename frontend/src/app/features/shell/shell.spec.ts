import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Api } from '../../core/api/api';
import { AuthService } from '../../core/auth/auth.service';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { displayName } from '../../core/auth/user-name';
import { Home } from './home';
import { Shell } from './shell';

describe('shell', () => {
  const auth = { signOut: vi.fn() };
  const user = signal({
    id: 'u1',
    email: 'anna@example.com',
    firstName: 'Anna' as string | null,
    lastName: 'Petrova' as string | null,
    roles: ['MENTOR'],
    permissions: [],
    phone: null,
  });
  const currentUser = {
    user,
    name: () => displayName(user()),
    hasPermission: (p: string) => (user().permissions as string[]).includes(p),
  };
  const api = { invoke: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: CurrentUserService, useValue: currentUser },
        { provide: Api, useValue: api },
      ],
    });
  });

  it('shows the name and email in the account menu and signs out', async () => {
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const trigger = element.querySelector('button') as HTMLButtonElement;
    expect(trigger.textContent).toContain('Anna Petrova');
    expect(trigger.getAttribute('aria-label')).toBe('Account menu for Anna Petrova');

    trigger.click();
    await fixture.whenStable();
    const overlay = document.body.textContent;
    expect(overlay).toContain('anna@example.com');
    const signOut = [...document.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === 'Sign out',
    );
    signOut?.click();
    expect(auth.signOut).toHaveBeenCalledOnce();
  });

  it('shows only the email when no name is set', async () => {
    user.update((u) => ({ ...u, firstName: null, lastName: null }));
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('button')?.textContent).toContain(
      'anna@example.com',
    );
    user.update((u) => ({ ...u, firstName: 'Anna', lastName: 'Petrova' }));
  });

  it.each([
    ['both names', 'Anna', 'Petrova', 'Anna Petrova'],
    ['only the first name', 'Anna', null, 'Anna'],
    ['only the last name', null, 'Petrova', 'Petrova'],
    ['the email when no name is set', null, null, 'anna@example.com'],
  ])('IDN-11.1 shows %s in the account menu', async (_case, firstName, lastName, expected) => {
    user.update((u) => ({ ...u, firstName, lastName }));
    try {
      const fixture = TestBed.createComponent(Shell);
      await fixture.whenStable();
      const trigger = (fixture.nativeElement as HTMLElement).querySelector('button');
      expect(trigger?.textContent).toContain(expected);
      expect(trigger?.getAttribute('aria-label')).toBe(`Account menu for ${expected}`);
    } finally {
      user.update((u) => ({ ...u, firstName: 'Anna', lastName: 'Petrova' }));
    }
  });

  it('greets the user and shows the version on the home page', async () => {
    api.invoke.mockResolvedValue({ name: 'MentorHub', version: '0.1.0' });
    const fixture = TestBed.createComponent(Home);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('h1')).toHaveLength(1);
    expect(element.querySelector('h1')?.textContent).toBe('Welcome, Anna');
    expect(element.querySelector('footer')?.textContent).toContain('MentorHub 0.1.0');
  });

  it('hides the version when the system info cannot be loaded', async () => {
    api.invoke.mockRejectedValue(new Error('down'));
    const fixture = TestBed.createComponent(Home);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('h1')?.textContent).toBe('Welcome, Anna');
    expect(element.querySelector('footer')?.textContent?.trim()).toBe('');
  });

  it('wraps the toolbar in a header landmark', async () => {
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('header mat-toolbar'),
    ).not.toBeNull();
  });

  it('shows the Users link only with USERS_READ', async () => {
    const link = (element: HTMLElement) =>
      [...element.querySelectorAll('a')].find((a) => a.textContent?.trim() === 'Users');
    const original = user();
    user.set({ ...original, permissions: ['USERS_READ'] as never[] });
    let fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(link(fixture.nativeElement)?.getAttribute('href')).toBe('/users');

    user.set({ ...original, permissions: [] });
    fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(link(fixture.nativeElement)).toBeUndefined();
  });

  it('shows the Invitations link only with USERS_INVITE', async () => {
    const link = (element: HTMLElement) =>
      [...element.querySelectorAll('a')].find((a) => a.textContent?.trim() === 'Invitations');
    const original = user();
    user.set({ ...original, permissions: ['USERS_READ', 'USERS_INVITE'] as never[] });
    let fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(link(fixture.nativeElement)?.getAttribute('href')).toBe('/invitations');

    user.set({ ...original, permissions: ['USERS_READ'] as never[] });
    fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(link(fixture.nativeElement)).toBeUndefined();
    user.set(original);
  });

  it('INV-16.1 offers Invitations in the toolbar to a holder of USERS_INVITE only', async () => {
    const link = (element: HTMLElement) =>
      [...element.querySelectorAll('a')].find((a) => a.textContent?.trim() === 'Invitations');
    const original = user();
    user.set({ ...original, permissions: ['USERS_READ', 'USERS_INVITE'] as never[] });
    let fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(link(fixture.nativeElement)?.getAttribute('href')).toBe('/invitations');

    user.set({ ...original, permissions: ['USERS_READ'] as never[] });
    fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(link(fixture.nativeElement)).toBeUndefined();
    user.set(original);
  });

  it('has Profile above Sign out in the account menu', async () => {
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    (fixture.nativeElement as HTMLElement).querySelector('button')?.click();
    await fixture.whenStable();
    const items = [...document.querySelectorAll('[mat-menu-item]')].map((i) =>
      i.textContent?.trim(),
    );
    expect(items).toEqual(['Profile', 'Sign out']);
    expect(document.querySelector('a[mat-menu-item]')?.getAttribute('href')).toBe('/profile');
  });
});
