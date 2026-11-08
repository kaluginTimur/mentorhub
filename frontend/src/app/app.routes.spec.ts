import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { Api } from './core/api/api';
import { AuthService } from './core/auth/auth.service';
import { CurrentUserService } from './core/auth/current-user.service';
import { displayName } from './core/auth/user-name';

describe('routes', () => {
  const user = {
    id: 'u1',
    email: 'anna@example.com',
    firstName: 'Anna',
    lastName: 'Petrova',
    roles: ['MENTOR'],
    permissions: [],
    phone: null,
  };

  const load = vi.fn();

  beforeEach(() => {
    load.mockReset().mockResolvedValue({ status: 'ACTIVE', user });
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        {
          provide: AuthService,
          useValue: {
            ensureSignedIn: vi.fn().mockResolvedValue({ kind: 'signed-in', returnUrl: null }),
            startupError: signal(false),
            signOut: vi.fn(),
          },
        },
        {
          provide: CurrentUserService,
          useValue: {
            load,
            user: () => user,
            name: () => displayName(user),
            hasPermission: () => false,
          },
        },
        { provide: Api, useValue: { invoke: vi.fn().mockResolvedValue({ name: 'MentorHub' }) } },
      ],
    });
  });

  it('leads a path that does not exist to the home page', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/no/such/page');
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('leads a users address to the home page without USERS_READ', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/users');
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('shows the application menu to an active user with roles', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    await harness.fixture.whenStable();
    expect(
      (harness.fixture.nativeElement as HTMLElement).querySelector('mat-toolbar'),
    ).not.toBeNull();
  });

  it.each([
    ['not linked', { status: 'NOT_INVITED' }, '/access/invitation-only'],
    ['disabled', { status: 'DISABLED' }, '/access/ended'],
    ['active without roles', { status: 'ACTIVE', user: { ...user, roles: [] } }, '/access/pending'],
  ])(
    'IDN-10.1 shows a user who is %s their access screen without the application menu',
    async (_name, me, url) => {
      load.mockResolvedValue(me);
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/');
      await harness.fixture.whenStable();
      harness.detectChanges();
      const element = harness.fixture.nativeElement as HTMLElement;
      expect(TestBed.inject(Router).url).toBe(url);
      expect(element.querySelector('h1')).not.toBeNull();
      expect(element.querySelector('mat-toolbar, nav, mat-menu')).toBeNull();
    },
  );
});
