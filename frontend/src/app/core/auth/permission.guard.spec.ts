import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { CurrentUserService } from './current-user.service';
import { requirePermission } from './permission.guard';

describe('requirePermission', () => {
  const permissions = signal<string[]>([]);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: CurrentUserService,
          useValue: { hasPermission: (p: string) => permissions().includes(p) },
        },
        {
          provide: Router,
          useValue: { parseUrl: (url: string) => ({ url }) as unknown as UrlTree },
        },
      ],
    });
  });

  const run = () =>
    TestBed.runInInjectionContext(() =>
      requirePermission('USERS_READ')({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

  it('lets users with the permission in', () => {
    permissions.set(['USERS_READ']);
    expect(run()).toBe(true);
  });

  it('redirects users without the permission to the home page', () => {
    permissions.set([]);
    expect(run()).toEqual({ url: '/' });
  });
});
