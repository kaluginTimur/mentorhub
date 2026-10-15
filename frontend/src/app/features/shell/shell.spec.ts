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
  });
  const currentUser = {
    user,
    name: () => displayName(user()),
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
});
