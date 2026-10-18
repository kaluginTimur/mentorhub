import { Service, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { firstValueFrom } from 'rxjs';
import { isCallbackUrl } from './api-urls';
import { InvitationSession } from './invitation-session';
import { readSession as read, writeSession as write } from './session-store';

const RETURN_URL_KEY = 'mentorhub.return-url';
const REDIRECT_TIMEOUT_MS = 15_000;
const SIGN_IN_KEY = 'mentorhub.sign-in-pending';

export type SignInResult =
  | { kind: 'signed-in'; returnUrl: string | null }
  | { kind: 'redirecting' }
  | { kind: 'invitation' }
  | { kind: 'failed' };

export interface SignInOptions {
  /** The email the identity provider prefills. */
  loginHint?: string;
  /** Opens the registration page instead of the sign-in page. */
  register?: boolean;
}

/** The signed-in account's identity from the ID token. */
export interface AccountIdentity {
  email: string | null;
}

@Service()
export class AuthService {
  private readonly oidc = inject(OidcSecurityService);
  private readonly router = inject(Router);
  private readonly invitation = inject(InvitationSession);
  private callbackChecked = false;

  /** True when sign-in cannot start or keeps failing: the full-page error is shown. */
  readonly startupError = signal(false);
  /** True while the browser is being sent to the identity provider. */
  readonly redirecting = signal(false);

  /**
   * Completes the sign-in callback on the first call, then only checks that the session is valid.
   * Starts sign-in when the visitor is not signed in.
   */
  async ensureSignedIn(url: string): Promise<SignInResult> {
    const isCallback = isCallbackUrl(url);
    try {
      if (await this.checkSession(url)) {
        const returnUrl = isCallback ? read(RETURN_URL_KEY) : null;
        if (isCallback) {
          write(RETURN_URL_KEY, null);
        }
        return { kind: 'signed-in', returnUrl };
      }
    } catch {
      return { kind: 'failed' };
    }
    // a callback that did not produce a session is a sign-in error, not a reason to try again
    if (isCallback) {
      return { kind: 'failed' };
    }
    // a visitor with an invitation in progress chooses how to sign in on the landing page
    if (this.invitation.token() !== null) {
      return { kind: 'invitation' };
    }
    return (await this.signIn(url)) ? { kind: 'redirecting' } : { kind: 'failed' };
  }

  /** Whether a session exists, without starting sign-in; false when it cannot be determined. */
  async isSignedIn(url: string): Promise<boolean> {
    try {
      return await this.checkSession(url);
    } catch {
      return false;
    }
  }

  /** The email from the ID token; null when the token has none. */
  async accountIdentity(): Promise<AccountIdentity> {
    try {
      const data = (await firstValueFrom(this.oidc.getUserData())) as {
        email?: unknown;
      } | null;
      return {
        email: typeof data?.email === 'string' ? data.email : null,
      };
    } catch {
      return { email: null };
    }
  }

  private async checkSession(url: string): Promise<boolean> {
    if (this.callbackChecked) {
      return firstValueFrom(this.oidc.isAuthenticated());
    }
    this.callbackChecked = true;
    const href = new URL(url, globalThis.location.origin).href;
    return (await firstValueFrom(this.oidc.checkAuth(href))).isAuthenticated;
  }

  /** Sends the browser to the identity provider; false when its discovery document cannot be loaded. */
  async signIn(returnUrl: string, options: SignInOptions = {}): Promise<boolean> {
    this.redirecting.set(true);
    try {
      await firstValueFrom(this.oidc.preloadAuthWellKnownDocument());
    } catch {
      this.redirecting.set(false);
      return false;
    }
    write(RETURN_URL_KEY, returnUrl);
    write(SIGN_IN_KEY, '1');
    // authorize() loads discovery again and reports failures nowhere: without a redirect in time, give up
    const watchdog = setTimeout(() => {
      this.redirecting.set(false);
      this.startupError.set(true);
    }, REDIRECT_TIMEOUT_MS);
    const customParams: Record<string, string> = {};
    if (options.loginHint) {
      customParams['login_hint'] = options.loginHint;
    }
    if (options.register) {
      customParams['prompt'] = 'create';
    }
    this.oidc.authorize(undefined, {
      customParams,
      urlHandler: (url) => {
        clearTimeout(watchdog);
        globalThis.location.assign(url);
      },
    });
    return true;
  }

  /** The API accepted a request: the last sign-in is known to work. */
  confirmSignIn(): void {
    if (read(SIGN_IN_KEY) !== null) {
      write(SIGN_IN_KEY, null);
    }
  }

  /** The API rejected the token: sign in again once, then give up. */
  handleUnauthorized(): void {
    if (this.redirecting() || this.startupError()) {
      return;
    }
    if (read(SIGN_IN_KEY) !== null) {
      write(SIGN_IN_KEY, null);
      this.startupError.set(true);
      return;
    }
    const navigation = this.router.currentNavigation();
    const target = navigation ? this.router.serializeUrl(navigation.extractedUrl) : this.router.url;
    void this.signIn(target).then((started) => {
      if (!started) {
        this.startupError.set(true);
      }
    });
  }

  getAccessToken(): Promise<string> {
    return firstValueFrom(this.oidc.getAccessToken());
  }

  signOut(): void {
    write(RETURN_URL_KEY, null);
    write(SIGN_IN_KEY, null);
    this.redirecting.set(true);
    let redirected = false;
    this.oidc
      .logoff(undefined, {
        urlHandler: (url) => {
          redirected = true;
          globalThis.location.assign(url);
        },
      })
      .subscribe({
        complete: () => {
          if (!redirected) {
            // no end-session URL: end the local session and sign in again
            this.oidc.logoffLocal();
            this.redirecting.set(false);
            void this.router.navigateByUrl('/');
          }
        },
        error: () => {
          // the Keycloak session may remain, so the error is shown instead of signing in again
          this.oidc.logoffLocal();
          this.redirecting.set(false);
          this.startupError.set(true);
        },
      });
  }
}
