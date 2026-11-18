import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { listMyEnrollments } from './core/api/fn/enrollments/list-my-enrollments';
import { listMyStudents } from './core/api/fn/enrollments/list-my-students';
import { getSystemInfo } from './core/api/fn/system/get-system-info';
import { Me } from './core/api/models/me';
import { MeUser } from './core/api/models/me-user';
import { AuthService } from './core/auth/auth.service';
import { CurrentUserService } from './core/auth/current-user.service';
import { displayName } from './core/auth/user-name';
import { createApiStub, provideApiStub } from './core/testing/api-stub';

describe('routes', () => {
  const user: MeUser = {
    id: 'u1',
    email: 'anna@example.com',
    firstName: 'Anna',
    lastName: 'Petrova',
    roles: ['MENTOR'],
    permissions: [],
    phone: null,
  };

  const load = vi.fn();
  const granted = new Set<string>();
  const api = createApiStub();

  beforeEach(() => {
    granted.clear();
    load.mockReset().mockResolvedValue({ status: 'ACTIVE', user } satisfies Me);
    api.reset();
    api.resolve(getSystemInfo, { name: 'MentorHub', version: '0.1.0' });
    api.resolve(listMyEnrollments, { items: [] });
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
            hasPermission: (p: string) => granted.has(p),
          },
        },
        provideApiStub(api),
      ],
    });
  });

  it('leads a path that does not exist to the home page', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/no/such/page');
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it.each([
    ['/users', 'USERS_READ'],
    ['/tracks', 'TRACKS_READ'],
    ['/tracks?status=ALL', 'TRACKS_READ'],
    ['/tracks/33333333-3333-4333-8333-333333333333', 'TRACKS_READ'],
  ])('leads %s to the home page without %s', async (url) => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('leads /my-students to the home page without ENROLLMENTS_READ_MENTORED', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/my-students');
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('opens /my-students to a user with ENROLLMENTS_READ_MENTORED', async () => {
    granted.add('ENROLLMENTS_READ_MENTORED');
    api.resolve(listMyStudents, { items: [], page: 0, size: 25, totalItems: 0 });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/my-students');
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/my-students');
  });

  it('opens My enrollment to an active user without any permission', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/my-enrollment');
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/my-enrollment');
  });

  it('shows the application menu to an active user with roles', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    await harness.fixture.whenStable();
    expect(
      (harness.fixture.nativeElement as HTMLElement).querySelector('header nav'),
    ).not.toBeNull();
  });

  it.each<[string, Me, string]>([
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
      expect(element.querySelector('header, nav')).toBeNull();
    },
  );
});
