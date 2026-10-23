import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { HttpClient, provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import {
  LogLevel,
  StsConfigHttpLoader,
  StsConfigLoader,
  provideAuth,
} from 'angular-auth-oidc-client';
import { map, shareReplay, tap } from 'rxjs';
import { provideApiConfiguration } from './core/api/api-configuration';
import { getClientConfig } from './core/api/fn/system/get-client-config';
import { ClientConfig } from './core/api/models/client-config';
import { ClientConfigService } from './core/client-config.service';
import { authInterceptor } from './core/auth/auth.interceptor';
import { errorInterceptor } from './core/auth/error.interceptor';
import { routes } from './app.routes';

export function oidcConfigLoader(
  http: HttpClient,
  onConfig?: (config: ClientConfig) => void,
): StsConfigLoader {
  const origin = globalThis.location.origin;
  return new StsConfigHttpLoader(
    getClientConfig(http, '').pipe(
      tap((response) => onConfig?.(response.body)),
      map((response) => ({
        authority: response.body.issuer,
        clientId: response.body.clientId,
        scope: response.body.scope,
        responseType: 'code',
        redirectUrl: `${origin}/`,
        postLogoutRedirectUri: `${origin}/`,
        silentRenew: true,
        useRefreshToken: true,
        // Keycloak issues refresh tokens without the offline_access scope
        disableRefreshTokenOfflineAccessScopeWarning: true,
        // routing after the callback stays with the guard, not the library
        triggerAuthorizationResultEvent: true,
        // the profile comes from /me; a failed userinfo call must not fail sign-in
        autoUserInfo: false,
        // refresh shortly before expiry so a request right after it never carries an expired token
        renewTimeBeforeTokenExpiresInSeconds: 30,
        logLevel: LogLevel.Warn,
      })),
      // sign-out re-reads the configuration: keep it once loaded so it works while the API is down
      shareReplay(1),
    ),
  );
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, errorInterceptor])),
    // contract paths already start with /api, so the base URL is empty
    provideApiConfiguration(''),
    provideAuth({
      loader: {
        provide: StsConfigLoader,
        useFactory: (http: HttpClient, config: ClientConfigService) =>
          oidcConfigLoader(http, (c) => config.apply(c)),
        deps: [HttpClient, ClientConfigService],
      },
    }),
  ],
};
