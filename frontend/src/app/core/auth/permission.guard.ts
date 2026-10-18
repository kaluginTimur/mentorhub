import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';
import { Permission } from '../api/models/permission';
import { CurrentUserService } from './current-user.service';

/** Lets a route tree open only for users who have the permission; others go to `/`. */
export function requirePermission(permission: Permission): CanActivateChildFn {
  return () =>
    inject(CurrentUserService).hasPermission(permission) ? true : inject(Router).parseUrl('/');
}
