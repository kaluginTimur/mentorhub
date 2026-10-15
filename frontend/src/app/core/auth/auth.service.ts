import { Service, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { firstValueFrom } from 'rxjs';

const RETURN_URL_KEY = 'mentorhub.return-url';
const REDIRECT_TIMEOUT_MS = 15_000;
const SIGN_IN_KEY = 'mentorhub.sign-in-pending';

export type SignInResult =
  { kind: 'signed-in'; returnUrl: string | null } | { kind: 'redirecting' } | { kind: 'failed' };

function read(key: string): string | null {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null): void {
  try {
    if (value === null) {
      sessionStorage.removeItem(key);
    } else {
      sessionStorage.setItem(key, value);
    }
  } catch {
    // storage is only a convenience: without it the user returns to the home page
  }
}

@Service()
export class AuthService {
  private readonly oidc = inject(OidcSecurityService);
  private readonly router = inject(Router);
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
    const isCallback = /[?&](code|error)=/.test(url);
    try {
      let authenticated: boolean;
      if (this.callbackChecked) {
        authenticated = await firstValueFrom(this.oidc.isAuthenticated());
      } else {
        this.callbackChecked = true;
        const href = new URL(url, globalThis.location.origin).href;
        authenticated = (await firstValueFrom(this.oidc.checkAuth(href))).isAuthenticated;
      }
      if (authenticated) {
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
    return (await this.signIn(url)) ? { kind: 'redirecting' } : { kind: 'failed' };
  }

  /** Sends the browser to the identity provider; false when its discovery document cannot be loaded. */
  async signIn(returnUrl: string): Promise<boolean> {
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
    this.oidc.authorize(undefined, {
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
