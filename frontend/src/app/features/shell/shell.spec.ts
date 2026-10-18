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

  it('shows the name in the account menu and signs out (AC-6, AC-8)', async () => {
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

  it('shows only the email when no name is set (AC-6)', async () => {
    user.update((u) => ({ ...u, firstName: null, lastName: null }));
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('button')?.textContent).toContain(
      'anna@example.com',
    );
    user.update((u) => ({ ...u, firstName: 'Anna', lastName: 'Petrova' }));
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

  it('shows the Users link only with USERS_READ (AC-22)', async () => {
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

  it('has Profile above Sign out in the account menu (AC-27)', async () => {
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
