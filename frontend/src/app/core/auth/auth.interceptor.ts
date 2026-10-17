import { HttpInterceptorFn } from '@angular/common/http';
import { Injector, inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { isApiRequest, isPublicApiRequest } from './api-urls';
import { AuthService } from './auth.service';

/** Attaches the access token to non-public requests to this application's API, and to nothing else. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isApiRequest(req.url) || isPublicApiRequest(req.url)) {
    return next(req);
  }
  // resolved lazily: the OIDC client itself loads its configuration through HttpClient
  const auth = inject(Injector).get(AuthService);
  return from(auth.getAccessToken()).pipe(
    switchMap((token) =>
      next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req),
    ),
  );
};
