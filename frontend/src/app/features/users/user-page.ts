import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  linkedSignal,
  resource,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { Api } from '../../core/api/api';
import { disableUser } from '../../core/api/fn/users/disable-user';
import { enableUser } from '../../core/api/fn/users/enable-user';
import { getUserHistory } from '../../core/api/fn/users/get-user-history';
import { getUser } from '../../core/api/fn/users/get-user';
import { setUserRoles } from '../../core/api/fn/users/set-user-roles';
import { updateUserProfile } from '../../core/api/fn/users/update-user-profile';
import { DisableRequest } from '../../core/api/models/disable-request';
import { HistoryEntry } from '../../core/api/models/history-entry';
import { ProfileUpdate } from '../../core/api/models/profile-update';
import { ROLE } from '../../core/api/models/role-array';
import { Role } from '../../core/api/models/role';
import { UserDetails } from '../../core/api/models/user-details';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { displayName } from '../../core/auth/user-name';
import { PROBLEM, isForbidden, problemOf } from '../../core/problem';
import { ConfirmDialog } from '../../shared/confirm-dialog/confirm-dialog';
import { Loading } from '../../shared/loading/loading';
import { Unavailable } from '../../shared/unavailable/unavailable';
import { DisableDialog } from './disable-dialog';
import { describeEntry } from './history-text';
import { REASON_LABELS, ROLE_LABELS, STATUS_LABELS } from './labels';
import { ProfileForm, ProfileValues } from '../../shared/profile-form/profile-form';
import { UsersListState } from './users-list-state';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

@Component({
  selector: 'app-user-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatChipsModule,
    MatProgressBarModule,
    Loading,
    ProfileForm,
    Unavailable,
  ],
  templateUrl: './user-page.html',
  styleUrl: './user-page.scss',
})
export class UserPage {
  private readonly api = inject(Api);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly currentUser = inject(CurrentUserService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly listState = inject(UsersListState);

  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private readonly routeParams = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });
  private readonly id = computed(() => this.routeParams().get('id') ?? '');

  protected readonly user = resource({
    params: () => this.id(),
    loader: async ({ params }): Promise<UserDetails | null> => {
      if (!UUID.test(params)) {
        return null;
      }
      try {
        return await this.api.invoke(getUser, { id: params });
      } catch (error) {
        if (error instanceof HttpErrorResponse && (error.status === 404 || error.status === 400)) {
          return null;
        }
        throw error;
      }
    },
  });

  protected readonly failed = computed(
    () => !!this.user.error() && !isForbidden(this.user.error()),
  );
  protected readonly canManage = computed(() => this.currentUser.hasPermission('USERS_MANAGE'));
  protected readonly isSelf = computed(() => {
    const user = this.user.hasValue() ? this.user.value() : null;
    return !!user && this.currentUser.user()?.id === user.id;
  });

  protected readonly editing = signal(false);
  protected readonly selectedRoles = linkedSignal<UserDetails | null | undefined, Role[]>({
    source: () => (this.user.hasValue() ? this.user.value() : null),
    computation: (user) => [...(user?.roles ?? [])],
  });
  protected readonly rolesChanged = computed(() => {
    const user = this.user.hasValue() ? this.user.value() : null;
    const selected = this.selectedRoles();
    return (
      !!user &&
      (selected.length !== user.roles.length || selected.some((r) => !user.roles.includes(r)))
    );
  });
  protected readonly savingRoles = signal(false);

  protected readonly entries = signal<HistoryEntry[]>([]);
  protected readonly historyTotal = signal(0);
  protected readonly historyLoading = signal(false);
  protected readonly historyFailed = signal(false);
  private nextHistoryPage = 0;
  private historyRequest = 0;

  protected readonly roles = ROLE;
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly reasonLabels = REASON_LABELS;
  protected readonly statusLabels = STATUS_LABELS;

  constructor() {
    effect(() => {
      if (isForbidden(this.user.error())) {
        void this.router.navigateByUrl('/');
      }
    });
    effect(() => {
      const id = this.id();
      untracked(() => {
        this.editing.set(false);
        this.entries.set([]);
        this.historyTotal.set(0);
        if (UUID.test(id)) {
          void this.loadHistory(true);
        }
      });
    });
  }

  protected name(user: UserDetails): string {
    return displayName(user);
  }

  protected disabledSummary(user: UserDetails): string {
    const reason = user.disabledReason ? REASON_LABELS[user.disabledReason] : '';
    return [reason, user.disabledComment].filter(Boolean).join(' — ');
  }

  protected describe(entry: HistoryEntry): string {
    return describeEntry(entry);
  }

  protected profileValues(user: UserDetails): ProfileValues {
    return user;
  }

  protected hasRole(role: Role): boolean {
    return this.selectedRoles().includes(role);
  }

  protected toggleRole(role: Role, checked: boolean): void {
    this.selectedRoles.update((current) =>
      ROLE.filter((r) => (r === role ? checked : current.includes(r))),
    );
  }

  protected resetRoles(user: UserDetails): void {
    this.selectedRoles.set([...user.roles]);
  }

  protected async loadHistory(reset: boolean): Promise<void> {
    const request = ++this.historyRequest;
    const page = reset ? 0 : this.nextHistoryPage;
    this.historyLoading.set(true);
    this.historyFailed.set(false);
    try {
      const result = await this.api.invoke(getUserHistory, { id: this.id(), page });
      if (request !== this.historyRequest) {
        return;
      }
      this.entries.update((current) => (reset ? result.items : [...current, ...result.items]));
      this.historyTotal.set(result.totalItems);
      this.nextHistoryPage = page + 1;
    } catch {
      if (request === this.historyRequest) {
        this.historyFailed.set(true);
      }
    } finally {
      if (request === this.historyRequest) {
        this.historyLoading.set(false);
      }
    }
  }

  protected saveProfile = async (body: ProfileUpdate): Promise<ProfileValues> => {
    const updated = await this.api.invoke(updateUserProfile, { id: this.id(), body });
    this.applied(updated, 'Profile saved', true);
    return updated;
  };

  protected async disable(user: UserDetails): Promise<void> {
    if (this.isSelf()) {
      return;
    }
    const ref = this.dialog.open(DisableDialog, {
      width: '480px',
      data: {
        name: this.name(user),
        save: (body: DisableRequest) => this.api.invoke(disableUser, { id: user.id, body }),
      },
    });
    const updated = await firstValueFrom(ref.afterClosed());
    if (updated) {
      this.applied(updated, 'User disabled', false);
      this.heading()?.nativeElement.focus();
    }
  }

  protected async enable(user: UserDetails): Promise<void> {
    const ref = this.dialog.open(ConfirmDialog, {
      width: '420px',
      data: {
        title: `Enable ${this.name(user)}?`,
        text: 'They will be able to sign in again.',
        confirmLabel: 'Enable',
      },
    });
    if (!(await firstValueFrom(ref.afterClosed()))) {
      return;
    }
    try {
      this.applied(await this.api.invoke(enableUser, { id: user.id }), 'User enabled', false);
      this.heading()?.nativeElement.focus();
    } catch (error) {
      this.showError(error, 'Could not enable the user. Try again.');
    }
  }

  protected async saveRoles(user: UserDetails): Promise<void> {
    this.savingRoles.set(true);
    try {
      const updated = await this.api.invoke(setUserRoles, {
        id: user.id,
        body: { roles: this.selectedRoles() },
      });
      this.applied(updated, 'Roles saved', true);
    } catch (error) {
      this.showError(error, 'Could not save the roles. Try again.');
    } finally {
      this.savingRoles.set(false);
    }
  }

  private applied(updated: UserDetails, message: string, refreshMe: boolean): void {
    this.user.set(updated);
    void this.loadHistory(true);
    this.snackBar.open(message, 'Dismiss', { duration: 5000, politeness: 'polite' });
    if (refreshMe && this.currentUser.user()?.id === updated.id) {
      this.currentUser.load().catch(() => undefined);
    }
  }

  private showError(error: unknown, fallback: string): void {
    const problem = problemOf(error);
    if (problem?.type === PROBLEM.selfLockout) {
      this.snackBar.open("You can't lock yourself out.", 'Dismiss', { politeness: 'assertive' });
    } else if (problem?.type === PROBLEM.lastAdmin) {
      this.snackBar.open('MentorHub must keep at least one active administrator.', 'Dismiss', {
        politeness: 'assertive',
      });
    } else if (problem?.status !== 403) {
      this.snackBar.open(fallback, 'Dismiss');
    }
  }
}
