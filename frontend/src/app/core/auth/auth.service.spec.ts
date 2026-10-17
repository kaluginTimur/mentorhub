import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { of, throwError } from 'rxjs';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  const oidc = {
    checkAuth: vi.fn(),
    isAuthenticated: vi.fn(),
    authorize: vi.fn(),
    logoff: vi.fn(),
    logoffLocal: vi.fn(),
    getAccessToken: vi.fn(),
    preloadAuthWellKnownDocument: vi.fn(),
  };
  const router = {
    currentNavigation: () => ({ extractedUrl: '/students' }),
    serializeUrl: (url: string) => url,
    url: '/',
    navigateByUrl: vi.fn(),
  };

  function create(): AuthService {
    TestBed.configureTestingModule({
      providers: [
        { provide: OidcSecurityService, useValue: oidc },
        { provide: Router, useValue: router },
      ],
    });
    return TestBed.inject(AuthService);
  }

  afterEach(() => vi.restoreAllMocks());

  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    oidc.preloadAuthWellKnownDocument.mockReturnValue(of({}));
  });

  it('starts sign-in for a visitor and remembers the route (AC-1)', async () => {
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: false }));
    const result = await create().ensureSignedIn('/students');
    expect(result).toEqual({ kind: 'redirecting' });
    expect(oidc.authorize).toHaveBeenCalledOnce();
    expect(sessionStorage.getItem('mentorhub.return-url')).toBe('/students');
  });

  it('returns the remembered route after the sign-in callback (AC-1)', async () => {
    sessionStorage.setItem('mentorhub.return-url', '/students');
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: true }));
    const result = await create().ensureSignedIn('/?code=abc&state=xyz');
    expect(result).toEqual({ kind: 'signed-in', returnUrl: '/students' });
    expect(sessionStorage.getItem('mentorhub.return-url')).toBeNull();
  });

  it('only checks the session on later navigations', async () => {
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: true }));
    oidc.isAuthenticated.mockReturnValue(of(true));
    const service = create();
    await service.ensureSignedIn('/');
    const result = await service.ensureSignedIn('/other');
    expect(result).toEqual({ kind: 'signed-in', returnUrl: null });
    expect(oidc.checkAuth).toHaveBeenCalledOnce();
  });

  it('fails instead of looping when the callback did not sign in', async () => {
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: false }));
    const result = await create().ensureSignedIn('/?error=access_denied&state=xyz');
    expect(result).toEqual({ kind: 'failed' });
    expect(oidc.authorize).not.toHaveBeenCalled();
  });

  it('fails when the OIDC client cannot start (client config unavailable)', async () => {
    oidc.checkAuth.mockReturnValue(throwError(() => new Error('down')));
    expect(await create().ensureSignedIn('/')).toEqual({ kind: 'failed' });
  });

  it('fails instead of waiting forever when discovery cannot be loaded', async () => {
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: false }));
    oidc.preloadAuthWellKnownDocument.mockReturnValue(throwError(() => new Error('down')));
    const service = create();
    expect(await service.ensureSignedIn('/students')).toEqual({ kind: 'failed' });
    expect(oidc.authorize).not.toHaveBeenCalled();
    expect(service.redirecting()).toBe(false);
  });

  it('shows the startup error when discovery fails during a 401 re-sign-in', async () => {
    oidc.preloadAuthWellKnownDocument.mockReturnValue(throwError(() => new Error('down')));
    const service = create();
    service.handleUnauthorized();
    await vi.waitFor(() => expect(service.startupError()).toBe(true));
    expect(oidc.authorize).not.toHaveBeenCalled();
  });

  it('signs in again once on 401, keeping the route, then shows the startup error (AC-28)', async () => {
    const service = create();
    service.handleUnauthorized();
    await vi.waitFor(() => expect(oidc.authorize).toHaveBeenCalledOnce());
    expect(oidc.authorize).toHaveBeenCalledOnce();
    expect(sessionStorage.getItem('mentorhub.return-url')).toBe('/students');
    service.redirecting.set(false); // the page was reloaded after the redirect

    service.handleUnauthorized();
    expect(oidc.authorize).toHaveBeenCalledOnce();
    expect(service.startupError()).toBe(true);
  });

  it('allows signing in again after the API accepted a token', async () => {
    const service = create();
    service.handleUnauthorized();
    await vi.waitFor(() => expect(oidc.authorize).toHaveBeenCalledOnce());
    service.redirecting.set(false);
    service.confirmSignIn();
    service.handleUnauthorized();
    await vi.waitFor(() => expect(oidc.authorize).toHaveBeenCalledTimes(2));
    expect(service.startupError()).toBe(false);
  });

  it('signs out at the identity provider (AC-8)', () => {
    const assign = vi.fn();
    vi.spyOn(globalThis, 'location', 'get').mockReturnValue({ assign } as unknown as Location);
    oidc.logoff.mockImplementation((_id, options) => {
      options.urlHandler('http://kc/logout');
      return of(null);
    });
    const service = create();
    service.signOut();
    expect(assign).toHaveBeenCalledWith('http://kc/logout');
    expect(oidc.logoffLocal).not.toHaveBeenCalled();
    expect(service.redirecting()).toBe(true);
  });

  it('ends the local session and goes home when logoff does not redirect', () => {
    oidc.logoff.mockReturnValue(of(null));
    const service = create();
    service.signOut();
    expect(oidc.logoffLocal).toHaveBeenCalledOnce();
    expect(service.redirecting()).toBe(false);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('ends the local session and shows the error when logoff fails', () => {
    oidc.logoff.mockReturnValue(throwError(() => new Error('down')));
    const service = create();
    service.signOut();
    expect(oidc.logoffLocal).toHaveBeenCalledOnce();
    expect(service.startupError()).toBe(true);
    expect(service.redirecting()).toBe(false);
  });

  it('gives up with the startup error when authorize never redirects', async () => {
    vi.useFakeTimers();
    try {
      oidc.checkAuth.mockReturnValue(of({ isAuthenticated: false }));
      const service = create();
      await service.ensureSignedIn('/students');
      expect(service.startupError()).toBe(false);
      vi.advanceTimersByTime(15_000);
      expect(service.startupError()).toBe(true);
      expect(service.redirecting()).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it('redirects through the url handler once authorize has the URL', async () => {
    const assign = vi.fn();
    vi.spyOn(globalThis, 'location', 'get').mockReturnValue({
      assign,
      origin: 'http://localhost:4200',
    } as unknown as Location);
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: false }));
    oidc.authorize.mockImplementation((_id, options) => options.urlHandler('http://kc/auth'));
    await create().ensureSignedIn('/students');
    expect(assign).toHaveBeenCalledWith('http://kc/auth');
  });
});
