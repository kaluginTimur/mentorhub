import { Routes } from '@angular/router';
import { accessGuard } from './core/auth/access.guard';
import { requirePermission } from './core/auth/permission.guard';

export const routes: Routes = [
  {
    path: 'unavailable',
    loadComponent: () =>
      import('./features/access/unavailable-page').then((m) => m.UnavailablePage),
  },
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
