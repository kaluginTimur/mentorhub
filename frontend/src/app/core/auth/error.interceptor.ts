import { HttpErrorResponse, HttpEventType, HttpInterceptorFn } from '@angular/common/http';
import { Injector, inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, tap, throwError } from 'rxjs';
import { isApiRequest, isPublicApiRequest } from './api-urls';
import { AuthService } from './auth.service';
import { ACCESS_PATHS, CurrentUserService, accessScreenOf } from './current-user.service';

const NOT_INVITED = 'urn:mentorhub:problem:not-invited';
const ACCESS_ENDED = 'urn:mentorhub:problem:access-ended';
const FORBIDDEN = 'urn:mentorhub:problem:forbidden';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isApiRequest(req.url)) {
    return next(req);
  }
  const injector = inject(Injector);
  const isPublic = isPublicApiRequest(req.url);

  return next(req).pipe(
    tap((event) => {
      if (!isPublic && event.type === HttpEventType.Response) {
        injector.get(AuthService).confirmSignIn();
      }
    }),
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && !isPublic) {
        if (error.status === 401) {
          injector.get(AuthService).handleUnauthorized();
        } else if (error.status === 403) {
          handleForbidden(injector, error);
        }
      }
      return throwError(() => error);
    }),
  );
};

function handleForbidden(injector: Injector, error: HttpErrorResponse): void {
  const type = (error.error as { type?: unknown } | null)?.type;
  if (type === NOT_INVITED || type === ACCESS_ENDED) {
    const currentUser = injector.get(CurrentUserService);
    const router = injector.get(Router);
    currentUser
      .load()
      .then((me) => router.navigateByUrl(ACCESS_PATHS[accessScreenOf(me)]))
      .catch((reloadError: unknown) => {
        if (reloadError instanceof HttpErrorResponse && reloadError.status === 401) {
          return;
        }
        currentUser.failedUrl = router.url;
        return router.navigateByUrl('/unavailable', { skipLocationChange: true });
      });
  } else if (type === FORBIDDEN) {
    // loaded on demand: a static import would pull the snack bar and its CDK code into the initial bundle
    void import('@angular/material/snack-bar').then(({ MatSnackBar }) =>
      injector.get(MatSnackBar).open("You don't have permission to do this.", 'Dismiss'),
    );
  }
}
