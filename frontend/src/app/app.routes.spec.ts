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

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        {
          provide: AuthService,
          useValue: {
            ensureSignedIn: vi.fn().mockResolvedValue({ kind: 'signed-in', returnUrl: null }),
            startupError: { set: vi.fn() },
            signOut: vi.fn(),
          },
        },
        {
          provide: CurrentUserService,
          useValue: {
            load: vi.fn().mockResolvedValue({ status: 'ACTIVE', user }),
            user: () => user,
            name: () => displayName(user),
            hasPermission: () => false,
          },
        },
        { provide: Api, useValue: { invoke: vi.fn().mockResolvedValue({ name: 'MentorHub' }) } },
      ],
    });
  });

  it('IDN-10.2 leads a path that does not exist to the home page', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/no/such/page');
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('USR-12.1 shows no Users link and leads a users address to the home page', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/users');
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/');
    const links = [...(harness.fixture.nativeElement as HTMLElement).querySelectorAll('a')];
    expect(links.map((a) => a.textContent?.trim())).not.toContain('Users');
  });
});
