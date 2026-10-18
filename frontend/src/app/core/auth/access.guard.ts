import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivateChildFn,
  GuardResult,
  RedirectCommand,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { isCallbackUrl, stripCallbackParams } from './api-urls';
import { AuthService } from './auth.service';
import { InvitationSession } from './invitation-session';
import {
  ACCESS_PATHS,
  AccessScreen,
  CurrentUserService,
  accessScreenOf,
} from './current-user.service';

/**
 * Signs the visitor in, reloads the caller on every navigation and routes by access status.
 * Child routes declare the status they are for in `data.access`.
 */
export const accessGuard: CanActivateChildFn = (route, state) => {
  // the router calls canActivateChild once per activated descendant; evaluate once per navigation
  let result = evaluated.get(state);
  if (!result) {
    result = evaluate(route, state);
    evaluated.set(state, result);
  }
  return result;
};

const evaluated = new WeakMap<RouterStateSnapshot, Promise<GuardResult>>();

function expectedAccess(route: ActivatedRouteSnapshot): AccessScreen {
  const declared = [...route.pathFromRoot]
    .reverse()
    .find((r) => r.routeConfig?.data?.['access'] !== undefined);
  return declared?.routeConfig?.data?.['access'] as AccessScreen;
}

async function evaluate(
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
): Promise<GuardResult> {
  const auth = inject(AuthService);
  const currentUser = inject(CurrentUserService);
  const router = inject(Router);
  const invitation = inject(InvitationSession);

  const signIn = await auth.ensureSignedIn(state.url);
  if (signIn.kind === 'redirecting') {
    return false;
  }
  if (signIn.kind === 'invitation') {
    return router.parseUrl('/invite');
  }
  if (signIn.kind === 'failed') {
    auth.startupError.set(true);
    return false;
  }

  // back from the identity provider with an invitation in progress: its landing page decides what is next
  if (isCallbackUrl(state.url) && invitation.token() !== null) {
    return router.parseUrl('/invite');
  }

  let actual: AccessScreen;
  try {
    actual = accessScreenOf(await currentUser.load());
  } catch (error) {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      // the error interceptor is already starting sign-in again or showing the startup error
      return false;
    }
    currentUser.failedUrl = signIn.returnUrl ?? stripCallbackParams(state.url);
    // the address bar keeps the requested URL, so a browser reload repeats the load
    return new RedirectCommand(router.parseUrl('/unavailable'), { skipLocationChange: true });
  }

  if (actual === 'full' && signIn.returnUrl) {
    return router.parseUrl(signIn.returnUrl);
  }
  const expected = expectedAccess(route);
  return expected === actual ? true : router.parseUrl(ACCESS_PATHS[actual]);
}
