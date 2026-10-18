import { Routes } from '@angular/router';
import { accessGuard } from './core/auth/access.guard';
import { requirePermission } from './core/auth/permission.guard';

export const routes: Routes = [
  {
    path: 'unavailable',
    loadComponent: () =>
      import('./features/access/unavailable-page').then((m) => m.UnavailablePage),
  },
  // public: outside the access guard, so a visitor without an account can open an invitation
  ...['invite/:token', 'invite'].map((path) => ({
    path,
    loadComponent: () => import('./features/invite/invite-page').then((m) => m.InvitePage),
  })),
  {
    path: '',
    canActivateChild: [accessGuard],
    children: [
      {
        path: '',
        data: { access: 'full' },
        loadComponent: () => import('./features/shell/shell').then((m) => m.Shell),
        children: [
          { path: '', loadComponent: () => import('./features/shell/home').then((m) => m.Home) },
          {
            path: 'users',
            canActivateChild: [requirePermission('USERS_READ')],
            children: [
              {
                path: '',
                loadComponent: () => import('./features/users/users-list').then((m) => m.UsersList),
              },
              {
                path: ':id',
                loadComponent: () => import('./features/users/user-page').then((m) => m.UserPage),
              },
            ],
          },
          {
            path: 'invitations',
            canActivateChild: [requirePermission('USERS_INVITE')],
            children: [
              {
                path: '',
                loadComponent: () =>
                  import('./features/invitations/invitations-list').then((m) => m.InvitationsList),
              },
            ],
          },
          {
            path: 'profile',
            loadComponent: () => import('./features/profile/my-profile').then((m) => m.MyProfile),
          },
        ],
      },
      ...(['invitation-only', 'ended', 'pending'] as const).map((access) => ({
        path: `access/${access}`,
        data: { access },
        loadComponent: () => import('./features/access/access-screen').then((m) => m.AccessScreen),
      })),
      { path: '**', redirectTo: '' },
    ],
  },
];
