import {
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  linkedSignal,
  resource,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucidePlus, lucideSearch } from '@ng-icons/lucide';
import { HlmBadge } from '@spartan-ng/helm/badge';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSelectImports } from '@spartan-ng/helm/select';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { Api } from '../../core/api/api';
import { listUsers } from '../../core/api/fn/users/list-users';
import { Role } from '../../core/api/models/role';
import { RoleFilter } from '../../core/api/models/role-filter';
import { ROLE_FILTER } from '../../core/api/models/role-filter-array';
import { UserPage } from '../../core/api/models/user-page';
import { USER_STATUS } from '../../core/api/models/user-status-array';
import { UserStatus } from '../../core/api/models/user-status';
import { UserSummary } from '../../core/api/models/user-summary';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { displayName } from '../../core/auth/user-name';
import { isForbidden } from '../../core/problem';
import { Avatar } from '../../shared/avatar/avatar';
import { Paginator } from '../../shared/paginator/paginator';
import { Unavailable } from '../../shared/unavailable/unavailable';
import { ROLE_LABELS, STATUS_LABELS } from './labels';
import { UsersListState } from './users-list-state';

const PAGE_SIZE = 25;
const SEARCH_DELAY = 300;
const SEARCH_LIMIT = 100;

@Component({
  selector: 'app-users-list',
  imports: [
    RouterLink,
    NgIcon,
    HlmButton,
    HlmBadge,
    HlmInput,
    HlmLabel,
    HlmSelectImports,
    HlmTableImports,
    Avatar,
    Paginator,
    Unavailable,
  ],
  providers: [provideIcons({ lucidePlus, lucideSearch })],
  templateUrl: './users-list.html',
})
export class UsersList {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(HlmDialogService);
  private readonly currentUser = inject(CurrentUserService);
  private readonly listState = inject(UsersListState);
  private pushedSearch: string | null = null;
  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly q = computed(() => this.params().get('q') ?? '');
  protected readonly status = computed(() => {
    const value = this.params().get('status');
    return USER_STATUS.find((s) => s === value) ?? ('' as const);
  });
  protected readonly role = computed(() => {
    const value = this.params().get('role');
    return ROLE_FILTER.find((r) => r === value) ?? ('' as const);
  });
  protected readonly pageIndex = computed(() => {
    const value = Number(this.params().get('page'));
    return Number.isInteger(value) && value > 0 ? value : 0;
  });
  protected readonly hasFilters = computed(
    () => this.q().trim() !== '' || this.status() !== '' || this.role() !== '',
  );

  /** The input follows the URL except for the value this component pushed itself. */
  protected readonly searchText = linkedSignal<string, string>({
    source: () => this.q(),
    computation: (q, previous) => (previous && q === this.pushedSearch ? previous.value : q),
  });

  protected readonly canInvite = computed(() => this.currentUser.hasPermission('USERS_INVITE'));

  protected readonly users = resource({
    params: () => ({
      q: this.q().trim().slice(0, SEARCH_LIMIT),
      status: this.status(),
      role: this.role(),
      page: this.pageIndex(),
    }),
    loader: ({ params }) =>
      this.api.invoke(listUsers, {
        q: params.q || undefined,
        status: params.status || undefined,
        role: params.role || undefined,
        page: params.page,
        size: PAGE_SIZE,
      }),
  });
  /** A failed load that is not a refusal; a refusal sends the user home. */
  protected readonly failed = computed(
    () => !!this.users.error() && !isForbidden(this.users.error()),
  );
  /** The last loaded page: it stays on screen while the next one loads. */
  protected readonly loaded = linkedSignal<UserPage | undefined, UserPage | undefined>({
    source: () => (this.users.hasValue() ? this.users.value() : undefined),
    computation: (page, previous) => page ?? previous?.value,
  });
  protected readonly rows = computed(() => this.loaded()?.items ?? []);

  protected readonly statuses = USER_STATUS;
  protected readonly roleFilters = ROLE_FILTER;
  protected readonly pageSize = PAGE_SIZE;
  protected readonly searchLimit = SEARCH_LIMIT;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly statusFilterLabel = (value: UserStatus | ''): string =>
    value === '' ? 'All' : STATUS_LABELS[value];
  protected readonly roleFilterText = (value: RoleFilter | ''): string =>
    value === '' ? 'Any role' : this.roleFilterLabel(value);

  constructor() {
    const destroyRef = inject(DestroyRef);
    destroyRef.onDestroy(() => clearTimeout(this.searchTimer));
    effect(() => {
      if (isForbidden(this.users.error())) {
        void this.router.navigateByUrl('/');
      }
    });
    effect(() => {
      const query: Params = {};
      for (const key of this.params().keys) {
        query[key] = this.params().get(key);
      }
      this.listState.query.set(query);
    });
  }

  protected async invite(): Promise<void> {
    // loaded on demand: the dialog and its form code are needed only when someone invites
    const { InviteDialog } = await import('../invitations/invite-dialog');
    this.dialog.open(InviteDialog, { contentClass: 'sm:max-w-[520px]' });
  }

  protected name(user: UserSummary): string {
    return displayName(user);
  }

  protected roleLabel(role: Role): string {
    return ROLE_LABELS[role];
  }

  protected statusLabel(status: UserStatus): string {
    return STATUS_LABELS[status];
  }

  protected roleFilterLabel(role: RoleFilter): string {
    return role === 'NONE' ? 'No roles' : ROLE_LABELS[role];
  }

  protected onSearch(value: string): void {
    this.searchText.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.pushedSearch = value.trim() ? value : '';
      this.go({ q: value.trim() ? value : null, page: null });
    }, SEARCH_DELAY);
  }

  protected setStatus(value: UserStatus | ''): void {
    this.go({ status: value || null, page: null });
  }

  protected setRole(value: RoleFilter | ''): void {
    this.go({ role: value || null, page: null });
  }

  protected onPage(pageIndex: number): void {
    this.go({ page: pageIndex || null });
  }

  protected clearFilters(): void {
    clearTimeout(this.searchTimer);
    this.pushedSearch = null;
    this.go({ q: null, status: null, role: null, page: null });
  }

  private go(queryParams: Params): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
    });
  }
}
