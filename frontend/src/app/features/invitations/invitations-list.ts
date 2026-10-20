import { DatePipe } from '@angular/common';
import {
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  linkedSignal,
  resource,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { firstValueFrom } from 'rxjs';
import { Api } from '../../core/api/api';
import { getInvitationLink } from '../../core/api/fn/invitations/get-invitation-link';
import { listInvitations } from '../../core/api/fn/invitations/list-invitations';
import { reissueInvitationLink } from '../../core/api/fn/invitations/reissue-invitation-link';
import { revokeInvitation } from '../../core/api/fn/invitations/revoke-invitation';
import { Invitation } from '../../core/api/models/invitation';
import { InvitationPage } from '../../core/api/models/invitation-page';
import { INVITATION_STATUS } from '../../core/api/models/invitation-status-array';
import { InvitationStatus } from '../../core/api/models/invitation-status';
import { Role } from '../../core/api/models/role';
import { PROBLEM, isForbidden, problemOf } from '../../core/problem';
import { ConfirmDialog } from '../../shared/confirm-dialog/confirm-dialog';
import { Unavailable } from '../../shared/unavailable/unavailable';
import { ROLE_LABELS } from '../users/labels';
import { InviteDialog, InviteDialogData } from './invite-dialog';
import { INVITATION_STATUS_LABELS } from './labels';

const PAGE_SIZE = 25;
const SEARCH_DELAY = 300;
const SEARCH_LIMIT = 254;

@Component({
  selector: 'app-invitations-list',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    Unavailable,
  ],
  templateUrl: './invitations-list.html',
  styleUrl: './invitations-list.scss',
})
export class InvitationsList {
  private readonly api = inject(Api);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private pushedSearch: string | null = null;
  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly q = computed(() => this.params().get('q') ?? '');
  protected readonly status = computed(() => {
    const value = this.params().get('status');
    return INVITATION_STATUS.find((s) => s === value) ?? ('' as const);
  });
  protected readonly pageIndex = computed(() => {
    const value = Number(this.params().get('page'));
    return Number.isInteger(value) && value > 0 ? value : 0;
  });
  protected readonly hasFilters = computed(() => this.q().trim() !== '' || this.status() !== '');

  /** The input follows the URL except for the value this component pushed itself. */
  protected readonly searchText = linkedSignal<string, string>({
    source: () => this.q(),
    computation: (q, previous) => (previous && q === this.pushedSearch ? previous.value : q),
  });

  protected readonly invitations = resource({
    params: () => ({
      q: this.q().trim().slice(0, SEARCH_LIMIT),
      status: this.status(),
      page: this.pageIndex(),
    }),
    loader: ({ params }) =>
      this.api.invoke(listInvitations, {
        q: params.q || undefined,
        status: params.status || undefined,
        page: params.page,
        size: PAGE_SIZE,
      }),
  });
  /** A failed load that is not a refusal; a refusal sends the user home. */
  protected readonly failed = computed(
    () => !!this.invitations.error() && !isForbidden(this.invitations.error()),
  );
  /** The last loaded page: it stays on screen while the next one loads. */
  protected readonly loaded = linkedSignal<InvitationPage | undefined, InvitationPage | undefined>({
    source: () => (this.invitations.hasValue() ? this.invitations.value() : undefined),
    computation: (page, previous) => page ?? previous?.value,
  });
  protected readonly rows = computed(() => this.loaded()?.items ?? []);

  /** Invitations whose row action is running; their actions stay disabled meanwhile. */
  private readonly busyIds = signal<ReadonlySet<string>>(new Set());

  protected readonly columns = ['email', 'roles', 'status', 'invited', 'actions'];
  protected readonly statuses = INVITATION_STATUS;
  protected readonly pageSize = PAGE_SIZE;
  protected readonly searchLimit = SEARCH_LIMIT;
  protected readonly statusLabels = INVITATION_STATUS_LABELS;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.searchTimer));
    effect(() => {
      if (isForbidden(this.invitations.error())) {
        void this.router.navigateByUrl('/');
      }
    });
  }

  protected roleLabel(role: Role): string {
    return ROLE_LABELS[role];
  }

  protected statusLabel(status: InvitationStatus): string {
    return INVITATION_STATUS_LABELS[status];
  }

  protected isBusy(id: string): boolean {
    return this.busyIds().has(id);
  }

  /** Keeps row elements (and the focus on a row button) across reloads. */
  protected readonly trackById = (_: number, invitation: Invitation): string => invitation.id;

  protected isOpen(invitation: Invitation): boolean {
    return invitation.status === 'PENDING' || invitation.status === 'EXPIRED';
  }

  protected onSearch(value: string): void {
    this.searchText.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.pushedSearch = value.trim() ? value : '';
      this.go({ q: value.trim() ? value : null, page: null });
    }, SEARCH_DELAY);
  }

  protected setStatus(value: InvitationStatus | ''): void {
    this.go({ status: value || null, page: null });
  }

  protected onPage(event: PageEvent): void {
    this.go({ page: event.pageIndex || null });
  }

  protected clearFilters(): void {
    clearTimeout(this.searchTimer);
    this.pushedSearch = null;
    this.go({ q: null, status: null, page: null });
  }

  protected async invite(): Promise<void> {
    await this.openAndReload({});
  }

  protected async copyLink(invitation: Invitation): Promise<void> {
    if (this.isBusy(invitation.id)) {
      return;
    }
    this.setBusy(invitation.id, true);
    // fetched before any await so that the clipboard write starts inside the click (Safari)
    const fetched = this.api.invoke(getInvitationLink, { id: invitation.id });
    fetched.catch(() => undefined);
    try {
      let copied = false;
      if (typeof ClipboardItem !== 'undefined' && navigator.clipboard?.write) {
        const blob = fetched.then((l) => new Blob([l.link], { type: 'text/plain' }));
        try {
          await navigator.clipboard.write([new ClipboardItem({ 'text/plain': blob })]);
          copied = true;
        } catch {
          // fall back to writeText below (or to the API error)
        }
      }
      const link = await fetched;
      if (!copied) {
        try {
          await navigator.clipboard.writeText(link.link);
          copied = true;
        } catch {
          // no clipboard at all: the copy view lets the user copy by hand
        }
      }
      if (copied) {
        this.snackBar.open('Link copied', 'Dismiss', { duration: 5000, politeness: 'polite' });
      } else {
        await this.openAndReload({ copy: link });
      }
    } catch (error) {
      this.rowActionFailed(error);
    } finally {
      this.setBusy(invitation.id, false);
    }
  }

  protected async revoke(invitation: Invitation): Promise<void> {
    if (this.isBusy(invitation.id)) {
      return;
    }
    const ref = this.dialog.open(ConfirmDialog, {
      width: '420px',
      data: {
        title: `Revoke the invitation for ${invitation.email}?`,
        text: 'The link will stop working.',
        confirmLabel: 'Revoke',
      },
    });
    if (!(await firstValueFrom(ref.afterClosed()))) {
      return;
    }
    try {
      await this.api.invoke(revokeInvitation, { id: invitation.id });
      this.snackBar.open('Invitation revoked', 'Dismiss', {
        duration: 5000,
        politeness: 'polite',
      });
      this.invitations.reload();
    } catch (error) {
      this.rowActionFailed(error);
    }
  }

  protected async newLink(invitation: Invitation): Promise<void> {
    if (this.isBusy(invitation.id)) {
      return;
    }
    const ref = this.dialog.open(ConfirmDialog, {
      width: '420px',
      data: {
        title: `Get a new link for ${invitation.email}?`,
        text: 'The previous link will stop working.',
        confirmLabel: 'Get a new link',
      },
    });
    if (!(await firstValueFrom(ref.afterClosed()))) {
      return;
    }
    try {
      const created = await this.api.invoke(reissueInvitationLink, { id: invitation.id });
      // the old link is already invalid, so the list changes even if the dialog is closed at once
      this.invitations.reload();
      await this.openAndReload({ created });
    } catch (error) {
      this.rowActionFailed(error);
    }
  }

  private setBusy(id: string, busy: boolean): void {
    this.busyIds.update((ids) => {
      const next = new Set(ids);
      if (busy) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }

  private async openAndReload(data: InviteDialogData): Promise<void> {
    const ref = this.dialog.open(InviteDialog, { width: '520px', data });
    await firstValueFrom(ref.afterClosed());
    this.invitations.reload();
  }

  private rowActionFailed(error: unknown): void {
    const problem = problemOf(error);
    if (problem?.type === PROBLEM.invitationClosed || problem?.status === 404) {
      this.snackBar.open('This invitation is already closed.', 'Dismiss', { politeness: 'polite' });
      this.invitations.reload();
    } else if (problem?.type === PROBLEM.invitationExpired) {
      this.snackBar.open('This invitation has expired. Get a new link.', 'Dismiss', {
        politeness: 'polite',
      });
      this.invitations.reload();
    } else if (problem?.status !== 403) {
      this.snackBar.open('Something went wrong. Try again.', 'Dismiss', { politeness: 'polite' });
    }
  }

  private go(queryParams: Params): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
    });
  }
}
