import { Service, computed, inject, signal } from '@angular/core';
import { Api } from '../api/api';
import { getMe } from '../api/fn/users/get-me';
import { Me } from '../api/models/me';
import { Permission } from '../api/models/permission';
import { displayName } from './user-name';

export type AccessScreen = 'full' | 'pending' | 'ended' | 'invitation-only';

export const ACCESS_PATHS: Record<AccessScreen, string> = {
  full: '/',
  pending: '/access/pending',
  ended: '/access/ended',
  'invitation-only': '/access/invitation-only',
};

export function accessScreenOf(me: Me): AccessScreen {
  switch (me.status) {
    case 'ACTIVE':
      return me.user && me.user.roles.length > 0 ? 'full' : 'pending';
    case 'DISABLED':
      return 'ended';
    case 'NOT_INVITED':
      return 'invitation-only';
    default: {
      const unreachable: never = me.status;
      return unreachable;
    }
  }
}

@Service()
export class CurrentUserService {
  private readonly api = inject(Api);

  readonly me = signal<Me | null>(null);
  readonly user = computed(() => this.me()?.user ?? null);
  readonly name = computed(() => {
    const user = this.user();
    return user ? displayName(user) : '';
  });
  hasPermission(permission: Permission): boolean {
    return this.user()?.permissions.includes(permission) ?? false;
  }

  /** The page a failed load was heading for, so "Retry" can repeat it. */
  failedUrl: string | null = null;

  async load(): Promise<Me> {
    const me = await this.api.invoke(getMe);
    this.me.set(me);
    this.failedUrl = null;
    return me;
  }
}
