import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { throwError, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { isApiRequest } from '../auth/api-urls';
import { FailureMode } from './failure-mode';

export const SLOW_DELAY_MS = 3000;

export const failureInterceptor: HttpInterceptorFn = (req, next) => {
  const mode = inject(FailureMode).mode();
  if (mode === 'normal' || !isApiRequest(req.url)) {
    return next(req);
  }
  switch (mode) {
    case 'slow':
      return timer(SLOW_DELAY_MS).pipe(switchMap(() => next(req)));
    case 'server-error':
      return throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            statusText: 'Internal Server Error',
            url: req.url,
            error: {
              type: 'about:blank',
              title: 'Internal Server Error',
              status: 500,
              instance: new URL(req.url, globalThis.location.origin).pathname,
            },
          }),
      );
    case 'offline':
      // status 0 with a progress event, as the browser reports a request that never reached the server
      return throwError(
        () =>
          new HttpErrorResponse({
            status: 0,
            statusText: 'Unknown Error',
            url: req.url,
            error: new ProgressEvent('error'),
          }),
      );
  }
};
