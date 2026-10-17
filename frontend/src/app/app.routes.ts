import { Routes } from '@angular/router';
import { accessGuard } from './core/auth/access.guard';

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
