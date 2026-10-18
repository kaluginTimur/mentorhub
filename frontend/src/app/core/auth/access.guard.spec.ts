import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  RedirectCommand,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Component } from '@angular/core';
import { Location } from '@angular/common';
import { provideRouter } from '@angular/router';
import { Me } from '../api/models/me';
import { accessGuard } from './access.guard';
import { AuthService, SignInResult } from './auth.service';
import { CurrentUserService } from './current-user.service';

describe('accessGuard', () => {
  const user = (
    roles: Me['user'] extends infer U ? (U extends { roles: infer R } ? R : never) : never,
  ) => ({
    id: 'u1',
    email: 'a@example.com',
    firstName: 'Anna',
    lastName: null,
    roles,
    permissions: [],
    phone: null,
  });
  const auth = { ensureSignedIn: vi.fn(), startupError: { set: vi.fn() } };
  const currentUser: { load: ReturnType<typeof vi.fn>; failedUrl: string | null } = {
    load: vi.fn(),
    failedUrl: null,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    currentUser.failedUrl = null;
    auth.ensureSignedIn.mockResolvedValue({ kind: 'signed-in', returnUrl: null } as SignInResult);
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: CurrentUserService, useValue: currentUser },
      ],
    });
  });

  async function run(access: string, url = '/'): Promise<boolean | string> {
    const route = {
      pathFromRoot: [{ routeConfig: { data: { access } } }],
    } as unknown as ActivatedRouteSnapshot;
    const result = await TestBed.runInInjectionContext(() =>
      accessGuard(route, { url } as RouterStateSnapshot),
    );
    const router = TestBed.inject(Router);
    if (result instanceof RedirectCommand) {
      expect(result.navigationBehaviorOptions?.skipLocationChange).toBe(true);
      return router.serializeUrl(result.redirectTo);
    }
    return result instanceof UrlTree ? router.serializeUrl(result) : (result as boolean);
  }

  it.each([
    ['active with roles', { status: 'ACTIVE', user: user(['MENTOR']) }, 'full', true],
    [
      'active with roles on an access route',
      { status: 'ACTIVE', user: user(['MENTOR']) },
      'pending',
      '/',
    ],
    ['active without roles', { status: 'ACTIVE', user: user([]) }, 'full', '/access/pending'],
    ['active without roles on its screen', { status: 'ACTIVE', user: user([]) }, 'pending', true],
    ['disabled', { status: 'DISABLED' }, 'full', '/access/ended'],
    ['not invited', { status: 'NOT_INVITED' }, 'full', '/access/invitation-only'],
    ['not invited on its screen', { status: 'NOT_INVITED' }, 'invitation-only', true],
    [
      'not invited on another access screen',
      { status: 'NOT_INVITED' },
      'ended',
      '/access/invitation-only',
    ],
  ] as [string, Me, string, boolean | string][])(
    'routes %s',
    async (_name, me, access, expected) => {
      currentUser.load.mockResolvedValue(me);
      expect(await run(access)).toBe(expected);
    },
  );

  it('reloads the caller on every navigation', async () => {
    currentUser.load.mockResolvedValue({ status: 'ACTIVE', user: user(['ADMIN']) });
    await run('full');
    await run('full');
    expect(currentUser.load).toHaveBeenCalledTimes(2);
  });

  it('returns to the route the visitor opened after sign-in (AC-1)', async () => {
    auth.ensureSignedIn.mockResolvedValue({ kind: 'signed-in', returnUrl: '/students' });
    currentUser.load.mockResolvedValue({ status: 'ACTIVE', user: user(['ADMIN']) });
    expect(await run('full', '/?code=a&state=b')).toBe('/students');
  });

  it('blocks while redirecting to sign-in', async () => {
    auth.ensureSignedIn.mockResolvedValue({ kind: 'redirecting' });
    expect(await run('full')).toBe(false);
    expect(currentUser.load).not.toHaveBeenCalled();
  });

  it('shows the startup error when sign-in fails', async () => {
    auth.ensureSignedIn.mockResolvedValue({ kind: 'failed' });
    expect(await run('full')).toBe(false);
    expect(auth.startupError.set).toHaveBeenCalledWith(true);
  });

  it('remembers the return URL, not the callback URL, when the load fails after sign-in', async () => {
    auth.ensureSignedIn.mockResolvedValue({ kind: 'signed-in', returnUrl: '/students' });
    currentUser.load.mockRejectedValue(new HttpErrorResponse({ status: 500 }));
    await run('full', '/?code=a&state=b&session_state=c');
    expect(currentUser.failedUrl).toBe('/students');
  });

  it('strips the callback parameters when there is no return URL', async () => {
    currentUser.load.mockRejectedValue(new HttpErrorResponse({ status: 500 }));
    await run('full', '/students?tab=1&code=a&state=b&iss=x');
    expect(currentUser.failedUrl).toBe('/students?tab=1');
  });

  it('blocks on 401 and leaves it to the error interceptor', async () => {
    currentUser.load.mockRejectedValue(new HttpErrorResponse({ status: 401 }));
    expect(await run('full')).toBe(false);
  });

  it('opens the unavailable page when the API fails, remembering the route for Retry', async () => {
    currentUser.load.mockRejectedValue(new HttpErrorResponse({ status: 500 }));
    expect(await run('full', '/students')).toBe('/unavailable');
    expect(currentUser.failedUrl).toBe('/students');
  });

  describe('in a real router', () => {
    @Component({ template: '' })
    class Page {}

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([
            {
              path: '',
              canActivateChild: [accessGuard],
              children: [
                {
                  path: '',
                  data: { access: 'full' },
                  component: Page,
                  children: [
                    { path: '', component: Page },
                    { path: 'students', component: Page },
                  ],
                },
                { path: 'access/pending', data: { access: 'pending' }, component: Page },
              ],
            },
            { path: 'unavailable', component: Page },
          ]),
        ],
      });
      currentUser.load.mockResolvedValue({ status: 'ACTIVE', user: user(['MENTOR']) });
    });

    it('loads the caller once per navigation', async () => {
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/');
      expect(currentUser.load).toHaveBeenCalledTimes(1);
    });

    it('lets a nested non-empty child inherit the access of its ancestor', async () => {
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/students');
      expect(TestBed.inject(Router).url).toBe('/students');
      expect(currentUser.load).toHaveBeenCalledTimes(1);
    });

    it('does not change the address when the load fails', async () => {
      currentUser.load.mockRejectedValue(new HttpErrorResponse({ status: 500 }));
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/students');
      expect(TestBed.inject(Router).url).toBe('/unavailable');
      expect(TestBed.inject(Location).path()).toBe('');
      expect(currentUser.failedUrl).toBe('/students');
    });
  });
});
