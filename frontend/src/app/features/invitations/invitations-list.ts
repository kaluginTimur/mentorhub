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
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideBan,
  lucideCopy,
  lucideEllipsisVertical,
  lucideMail,
  lucidePlus,
  lucideRefreshCw,
  lucideSearch,
} from '@ng-icons/lucide';
import { HlmBadge } from '@spartan-ng/helm/badge';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmDropdownMenuImports } from '@spartan-ng/helm/dropdown-menu';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSelectImports } from '@spartan-ng/helm/select';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { firstValueFrom } from 'rxjs';
import { Api } from '../../core/api/api';
import { getInvitationLink } from '../../core/api/fn/invitations/get-invitation-link';
import { listInvitations } from '../../core/api/fn/invitations/list-invitations';
import { reissueInvitationLink } from '../../core/api/fn/invitations/reissue-invitation-link';
import { revokeInvitation } from '../../core/api/fn/invitations/revoke-invitation';
import { sendInvitationEmail } from '../../core/api/fn/invitations/send-invitation-email';
import { Invitation } from '../../core/api/models/invitation';
import { InvitationPage } from '../../core/api/models/invitation-page';
import { INVITATION_STATUS } from '../../core/api/models/invitation-status-array';
import { InvitationStatus } from '../../core/api/models/invitation-status';
import { Role } from '../../core/api/models/role';
import { ClientConfigService } from '../../core/client-config.service';
import { PROBLEM, isForbidden, problemOf } from '../../core/problem';
import { Avatar } from '../../shared/avatar/avatar';
import {
  ConfirmDialog,
  ConfirmDialogData,
  ConfirmResult,
} from '../../shared/confirm-dialog/confirm-dialog';
import { Paginator } from '../../shared/paginator/paginator';
import { ToastService } from '../../shared/toast/toast.service';
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
    NgIcon,
    HlmButton,
    HlmBadge,
    HlmDropdownMenuImports,
    HlmInput,
    HlmLabel,
    HlmSelectImports,
    HlmTableImports,
    Avatar,
    Paginator,
    Unavailable,
  ],
  providers: [
    provideIcons({
      lucideBan,
      lucideCopy,
      lucideEllipsisVertical,
      lucideMail,
      lucidePlus,
      lucideRefreshCw,
      lucideSearch,
    }),
  ],
  templateUrl: './invitations-list.html',
})
export class InvitationsList {
  private readonly api = inject(Api);
  private readonly dialog = inject(HlmDialogService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly emailEnabled = inject(ClientConfigService).invitationEmails;
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

  protected readonly statuses = INVITATION_STATUS;
  protected readonly pageSize = PAGE_SIZE;
  protected readonly searchLimit = SEARCH_LIMIT;
  protected readonly statusLabels = INVITATION_STATUS_LABELS;
  protected readonly statusFilterLabel = (value: InvitationStatus | ''): string =>
    value === '' ? 'All' : INVITATION_STATUS_LABELS[value];

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

  protected statusBadgeClass(status: InvitationStatus): string {
    switch (status) {
      case 'PENDING':
        return 'border-transparent bg-mh-warning-soft text-mh-warning-soft-foreground';
      case 'ACCEPTED':
        return 'border-transparent bg-mh-success-soft text-mh-success-soft-foreground';
      case 'EXPIRED':
        return 'text-muted-foreground';
      case 'REVOKED':
        return '';
    }
  }

  protected statusDotClass(status: InvitationStatus): string {
    switch (status) {
      case 'PENDING':
        return 'bg-mh-warning';
      case 'ACCEPTED':
        return 'bg-mh-success';
      case 'EXPIRED':
        return 'bg-muted-foreground';
      case 'REVOKED':
        return 'bg-destructive';
    }
  }

  protected isBusy(id: string): boolean {
    return this.busyIds().has(id);
  }

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

  protected onPage(pageIndex: number): void {
    this.go({ page: pageIndex || null });
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
        this.toast.show('Link copied', { duration: 5000, politeness: 'polite' });
      } else {
        await this.openAndReload({ copy: link });
      }
    } catch (error) {
      this.rowActionFailed(error);
    } finally {
      this.setBusy(invitation.id, false);
    }
  }

  protected async sendEmail(invitation: Invitation): Promise<void> {
    if (this.isBusy(invitation.id)) {
      return;
    }
    this.setBusy(invitation.id, true);
    try {
      const updated = await this.api.invoke(sendInvitationEmail, { id: invitation.id });
      this.loaded.update((page) =>
        page
          ? { ...page, items: page.items.map((i) => (i.id === updated.id ? updated : i)) }
          : page,
      );
      this.toast.show(`Email to ${invitation.email} queued`, {
        duration: 5000,
        politeness: 'polite',
      });
    } catch (error) {
      this.rowActionFailed(error, invitation);
    } finally {
      this.setBusy(invitation.id, false);
    }
  }

  protected async revoke(invitation: Invitation): Promise<void> {
    if (this.isBusy(invitation.id)) {
      return;
    }
    const ref = this.dialog.open<boolean, ConfirmDialogData>(ConfirmDialog, {
      contentClass: 'sm:max-w-[420px]',
      context: {
        title: `Revoke the invitation for ${invitation.email}?`,
        text: 'The link will stop working.',
        confirmLabel: 'Revoke',
      },
    });
    if (!(await firstValueFrom(ref.closed$))) {
      return;
    }
    try {
      await this.api.invoke(revokeInvitation, { id: invitation.id });
      this.toast.show('Invitation revoked', {
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
    const ref = this.dialog.open<true | ConfirmResult, ConfirmDialogData>(ConfirmDialog, {
      contentClass: 'sm:max-w-[420px]',
      context: {
        title: `Get a new link for ${invitation.email}?`,
        text: 'The previous link will stop working.',
        confirmLabel: 'Get a new link',
        ...(this.emailEnabled()
          ? { checkbox: { label: 'Send the new link by email', checked: true } }
          : {}),
      },
    });
    const result = await firstValueFrom(ref.closed$);
    if (!result) {
      return;
    }
    const emailRequested = this.emailEnabled() && result !== true && result.checked;
    try {
      const created = await this.api.invoke(reissueInvitationLink, {
        id: invitation.id,
        ...(this.emailEnabled() ? { body: { sendEmail: emailRequested } } : {}),
      });
      // the old link is already invalid, so the list changes even if the dialog is closed at once
      this.invitations.reload();
      await this.openAndReload({ created, emailRequested });
    } catch (error) {
      this.rowActionFailed(error, invitation);
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

  private async openAndReload(context: InviteDialogData): Promise<void> {
    const ref = this.dialog.open<void, InviteDialogData>(InviteDialog, {
      contentClass: 'sm:max-w-[520px]',
      context,
      ...(context.copy || context.created ? { autoFocus: '#invitation-link-field' } : {}),
    });
    await firstValueFrom(ref.closed$);
    this.invitations.reload();
  }

  private rowActionFailed(error: unknown, invitation?: Invitation): void {
    const problem = problemOf(error);
    if (problem?.type === PROBLEM.emailUnavailable) {
      this.toast.show('Sending email is not available. Reload the page and try again.', {
        politeness: 'assertive',
      });
    } else if (problem?.type === PROBLEM.emailQueued) {
      this.toast.show(`An email to ${invitation?.email ?? 'this address'} is already on its way.`, {
        politeness: 'assertive',
      });
      this.invitations.reload();
    } else if (problem?.type === PROBLEM.invitationClosed || problem?.status === 404) {
      this.toast.show('This invitation is already closed.', { politeness: 'polite' });
      this.invitations.reload();
    } else if (problem?.type === PROBLEM.invitationExpired) {
      this.toast.show('This invitation has expired. Get a new link.', {
        politeness: 'polite',
      });
      this.invitations.reload();
    } else if (problem?.status !== 403) {
      this.toast.show('Something went wrong. Try again.', { politeness: 'polite' });
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
