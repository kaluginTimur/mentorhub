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
    getUserData: vi.fn(),
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

  it('sends a visitor with an invitation in progress to its landing page instead of signing in', async () => {
    sessionStorage.setItem('mentorhub.invitation-token', 'tok');
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: false }));
    expect(await create().ensureSignedIn('/')).toEqual({ kind: 'invitation' });
    expect(oidc.authorize).not.toHaveBeenCalled();
  });

  it('passes the email hint and the registration prompt to the identity provider', async () => {
    const service = create();
    await service.signIn('/invite', { loginHint: 'ivan@example.com', register: true });
    expect(oidc.authorize).toHaveBeenCalledWith(
      undefined,
      expect.objectContaining({
        customParams: { login_hint: 'ivan@example.com', prompt: 'create' },
      }),
    );
    expect(sessionStorage.getItem('mentorhub.return-url')).toBe('/invite');

    await service.signIn('/invite', { loginHint: 'ivan@example.com' });
    expect(oidc.authorize).toHaveBeenLastCalledWith(
      undefined,
      expect.objectContaining({ customParams: { login_hint: 'ivan@example.com' } }),
    );
    await service.signIn('/');
    expect(oidc.authorize).toHaveBeenLastCalledWith(
      undefined,
      expect.objectContaining({ customParams: {} }),
    );
  });

  it('tells whether a session exists without starting sign-in', async () => {
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: true }));
    const service = create();
    expect(await service.isSignedIn('/invite')).toBe(true);
    oidc.isAuthenticated.mockReturnValue(of(false));
    expect(await service.isSignedIn('/invite')).toBe(false);
    oidc.isAuthenticated.mockReturnValue(throwError(() => new Error('down')));
    expect(await service.isSignedIn('/invite')).toBe(false);
    expect(oidc.authorize).not.toHaveBeenCalled();
  });

  it('reads the account email and its verification from the ID token', async () => {
    const service = create();
    oidc.getUserData.mockReturnValue(of({ email: 'a@example.com', email_verified: true }));
    expect(await service.accountIdentity()).toEqual({
      email: 'a@example.com',
    });
    oidc.getUserData.mockReturnValue(of(null));
    expect(await service.accountIdentity()).toEqual({ email: null });
    oidc.getUserData.mockReturnValue(throwError(() => new Error('down')));
    expect(await service.accountIdentity()).toEqual({ email: null });
  });

  it('keeps the invitation token when signing out', () => {
    sessionStorage.setItem('mentorhub.invitation-token', 'tok');
    oidc.logoff.mockReturnValue(of(null));
    create().signOut();
    expect(sessionStorage.getItem('mentorhub.invitation-token')).toBe('tok');
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

  it('restarts sign-in once when the callback has no session (opened in another tab)', async () => {
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: false }));
    const result = await create().ensureSignedIn('/?code=abc&state=xyz');
    expect(result).toEqual({ kind: 'redirecting' });
    expect(oidc.authorize).toHaveBeenCalledOnce();
    expect(sessionStorage.getItem('mentorhub.return-url')).toBe('/');
    expect(sessionStorage.getItem('mentorhub.callback-retry')).toBe('1');
  });

  it('restarts sign-in once when the library rejects the callback state (opened in another tab)', async () => {
    oidc.checkAuth.mockReturnValue(throwError(() => new Error('could not find matching config')));
    expect(await create().ensureSignedIn('/?code=abc&state=xyz')).toEqual({ kind: 'redirecting' });
    expect(oidc.authorize).toHaveBeenCalledOnce();
  });

  it('restarts sign-in once when the identity provider reports an expired authentication', async () => {
    sessionStorage.setItem('mentorhub.return-url', '/students');
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: false }));
    const url = '/?error=temporarily_unavailable&error_description=authentication_expired&state=x';
    expect(await create().ensureSignedIn(url)).toEqual({ kind: 'redirecting' });
    expect(oidc.authorize).toHaveBeenCalledOnce();
    expect(sessionStorage.getItem('mentorhub.return-url')).toBe('/students');
  });

  it('keeps the invitation across the retry', async () => {
    sessionStorage.setItem('mentorhub.invitation-token', 'tok');
    sessionStorage.setItem('mentorhub.invitation-auto-accept', '1');
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: false }));
    const url = '/?error=temporarily_unavailable&state=x';
    expect(await create().ensureSignedIn(url)).toEqual({ kind: 'redirecting' });
    expect(sessionStorage.getItem('mentorhub.invitation-token')).toBe('tok');
    expect(sessionStorage.getItem('mentorhub.invitation-auto-accept')).toBe('1');
  });

  it('fails when the retried callback fails again, and allows a later retry', async () => {
    sessionStorage.setItem('mentorhub.callback-retry', '1');
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: false }));
    const result = await create().ensureSignedIn('/?error=temporarily_unavailable&state=x');
    expect(result).toEqual({ kind: 'failed' });
    expect(oidc.authorize).not.toHaveBeenCalled();
    expect(sessionStorage.getItem('mentorhub.callback-retry')).toBeNull();
  });

  it('fails and leaves no marker when the retry cannot start', async () => {
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: false }));
    oidc.preloadAuthWellKnownDocument.mockReturnValue(throwError(() => new Error('down')));
    expect(await create().ensureSignedIn('/?code=abc&state=xyz')).toEqual({ kind: 'failed' });
    expect(sessionStorage.getItem('mentorhub.callback-retry')).toBeNull();
  });

  it('allows a new retry after an abandoned one once a fresh sign-in starts', async () => {
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: false }));
    const service = create();
    await service.ensureSignedIn('/?code=abc&state=xyz');
    expect(sessionStorage.getItem('mentorhub.callback-retry')).toBe('1');
    await service.signIn('/students');
    expect(sessionStorage.getItem('mentorhub.callback-retry')).toBeNull();
  });

  it('does not retry when the user cancelled sign-in', async () => {
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: false }));
    const result = await create().ensureSignedIn('/?error=access_denied&state=xyz');
    expect(result).toEqual({ kind: 'failed' });
    expect(oidc.authorize).not.toHaveBeenCalled();
  });

  it('clears the retry marker after a successful sign-in', async () => {
    sessionStorage.setItem('mentorhub.callback-retry', '1');
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: true }));
    await create().ensureSignedIn('/?code=abc&state=xyz');
    expect(sessionStorage.getItem('mentorhub.callback-retry')).toBeNull();
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
