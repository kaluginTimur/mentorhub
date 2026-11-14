import { Component, computed, inject, signal } from '@angular/core';
import { FormField, form, required, submit, validate } from '@angular/forms/signals';
import { RouterLink } from '@angular/router';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCheckbox } from '@spartan-ng/helm/checkbox';
import { HlmDialogFooter, HlmDialogHeader, HlmDialogTitle } from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { Api } from '../../core/api/api';
import { createInvitation } from '../../core/api/fn/invitations/create-invitation';
import { CreatedInvitation } from '../../core/api/models/created-invitation';
import { InvitationLink } from '../../core/api/models/invitation-link';
import { Role } from '../../core/api/models/role';
import { ROLE } from '../../core/api/models/role-array';
import { ClientConfigService } from '../../core/client-config.service';
import { PROBLEM, problemOf } from '../../core/problem';
import { ROLE_LABELS } from '../users/labels';
import { LinkView } from './link-view';

export interface InviteDialogData {
  /** Opens the link view directly, for a link that has just been issued. */
  created?: CreatedInvitation;
  /** Opens the copy view directly, when the clipboard could not be written. */
  copy?: InvitationLink;
  /** With `created`: the email was requested together with the new link. */
  emailRequested?: boolean;
}

interface LinkShown {
  link: InvitationLink;
  mode: 'new' | 'copy';
  emailRequested: boolean;
}

function toLink(created: CreatedInvitation): InvitationLink {
  return {
    link: created.link,
    email: created.invitation.email,
    expiresAt: created.invitation.expiresAt,
  };
}

function initialView(data: InviteDialogData | null): LinkShown | null {
  if (data?.created) {
    return {
      link: toLink(data.created),
      mode: 'new',
      emailRequested: data.emailRequested === true,
    };
  }
  return data?.copy ? { link: data.copy, mode: 'copy', emailRequested: false } : null;
}

type Conflict = { kind: 'user-active'; userId: string } | { kind: 'invitation-exists' };

const EMAIL_UNAVAILABLE = 'Sending email is not available. Reload the page and try again.';

const EMAIL_LIMIT = 254;
const EMAIL_FORMAT = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

@Component({
  selector: 'app-invite-dialog',
  imports: [
    RouterLink,
    FormField,
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogFooter,
    HlmButton,
    HlmCheckbox,
    HlmInput,
    HlmLabel,
    LinkView,
  ],
  template: `
    @if (shown(); as s) {
      <app-link-view
        [link]="s.link"
        [mode]="s.mode"
        [emailRequested]="s.emailRequested"
        (done)="ref.close()"
      />
    } @else {
      <div class="flex flex-col gap-5">
        <div hlmDialogHeader>
          <h2 hlmDialogTitle class="font-display text-lg font-semibold tracking-tight">
            Invite a person
          </h2>
        </div>

        <form novalidate (submit)="onSubmit($event)" class="space-y-4">
          <div class="flex flex-col gap-2.5">
            <label hlmLabel for="invite-email">Email</label>
            <input
              hlmInput
              id="invite-email"
              type="email"
              autocomplete="off"
              [formField]="inviteForm.email"
              [forceInvalid]="inviteForm.email().touched() && inviteForm.email().invalid()"
              [attr.aria-invalid]="
                inviteForm.email().touched() && inviteForm.email().invalid() ? 'true' : null
              "
              [attr.aria-describedby]="
                inviteForm.email().touched() && inviteForm.email().errors().length > 0
                  ? 'invite-email-error'
                  : null
              "
            />
            @if (inviteForm.email().touched() && inviteForm.email().errors().length > 0) {
              <div id="invite-email-error" class="field-error space-y-0.5 text-xs text-destructive">
                @for (error of inviteForm.email().errors(); track error.message) {
                  <p>{{ error.message }}</p>
                }
              </div>
            }
          </div>

          <fieldset aria-describedby="roles-hint" class="flex flex-col gap-2">
            <legend class="mb-1 text-sm font-medium">Roles</legend>
            @for (role of roles; track role) {
              <label
                hlmLabel
                [for]="'invite-role-' + role"
                class="flex cursor-pointer items-center gap-2.5 font-normal"
              >
                <hlm-checkbox
                  [inputId]="'invite-role-' + role"
                  [checked]="selected().includes(role)"
                  (checkedChange)="toggle(role, $event)"
                />
                <span>{{ labels[role] }}</span>
              </label>
            }
            <p class="mt-0.5 text-xs text-muted-foreground" id="roles-hint">
              Without roles the person can sign in but has no access to anything.
            </p>
            @if (rolesError(); as message) {
              <p class="error text-xs text-destructive" role="alert">{{ message }}</p>
            }
          </fieldset>

          @if (emailEnabled) {
            <label
              hlmLabel
              for="invite-send-email"
              class="flex cursor-pointer items-center gap-2.5 pt-1 font-normal"
            >
              <hlm-checkbox
                inputId="invite-send-email"
                [checked]="sendEmail()"
                (checkedChange)="sendEmail.set($event)"
              />
              <span>Send the invitation by email to this address</span>
            </label>
          }

          @if (conflict(); as c) {
            <div
              class="conflict flex flex-wrap items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive"
              role="alert"
            >
              @if (c.kind === 'user-active') {
                <span>This person already has access.</span>
                <a
                  hlmBtn
                  variant="link"
                  size="sm"
                  class="h-auto p-0"
                  [routerLink]="['/users', c.userId]"
                  (click)="ref.close()"
                >
                  Open user
                </a>
              } @else {
                <span>There is already an invitation for this email.</span>
                <a
                  hlmBtn
                  variant="link"
                  size="sm"
                  class="h-auto p-0"
                  routerLink="/invitations"
                  [queryParams]="{ q: trimmedEmail() }"
                  (click)="ref.close()"
                >
                  Show invitation
                </a>
              }
            </div>
          }
          @if (emailUnavailable()) {
            <p class="error text-xs text-destructive" role="alert">{{ emailUnavailableText }}</p>
          }
          @if (failure()) {
            <p class="error text-xs text-destructive" role="alert">
              Something went wrong. Try again.
            </p>
          }

          <div hlmDialogFooter>
            <button hlmBtn variant="outline" type="button" (click)="ref.close()">Cancel</button>
            <button hlmBtn type="submit" [disabled]="saving()">Create invitation</button>
          </div>
        </form>
      </div>
    }
  `,
})
export class InviteDialog {
  private readonly api = inject(Api);
  private readonly data = injectBrnDialogContext<InviteDialogData | null>({ optional: true });
  protected readonly ref = inject<BrnDialogRef<void>>(BrnDialogRef);

  protected readonly emailEnabled = inject(ClientConfigService).invitationEmails();
  protected readonly emailUnavailableText = EMAIL_UNAVAILABLE;
  protected readonly sendEmail = signal(true);
  protected readonly emailUnavailable = signal(false);
  protected readonly roles = ROLE;
  protected readonly labels = ROLE_LABELS;
  protected readonly shown = signal<LinkShown | null>(initialView(this.data));
  protected readonly saving = signal(false);
  protected readonly selected = signal<Role[]>([]);
  protected readonly rolesError = signal('');
  protected readonly conflict = signal<Conflict | null>(null);
  protected readonly failure = signal(false);

  private readonly model = signal({ email: '' });
  protected readonly inviteForm = form(this.model, (path) => {
    required(path.email, { message: 'Enter an email' });
    validate(path.email, ({ value }) => {
      const text = value().trim();
      if (value() === '') {
        return undefined;
      }
      if (text === '') {
        return { kind: 'required', message: 'Enter an email' };
      }
      if (text.length > EMAIL_LIMIT) {
        return { kind: 'maxLength', message: `Use at most ${EMAIL_LIMIT} characters` };
      }
      return EMAIL_FORMAT.test(text)
        ? undefined
        : { kind: 'email', message: 'Enter a valid email address' };
    });
  });
  protected readonly trimmedEmail = computed(() => this.model().email.trim());

  protected toggle(role: Role, checked: boolean): void {
    this.selected.update((current) =>
      ROLE.filter((r) => (r === role ? checked : current.includes(r))),
    );
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.conflict.set(null);
    this.failure.set(false);
    this.emailUnavailable.set(false);
    this.rolesError.set('');
    try {
      await submit(this.inviteForm, async (field) => {
        try {
          const emailRequested = this.emailEnabled && this.sendEmail();
          const result = await this.api.invoke(createInvitation, {
            body: {
              email: field().value().email.trim(),
              roles: this.selected(),
              ...(this.emailEnabled ? { sendEmail: emailRequested } : {}),
            },
          });
          this.shown.set({ link: toLink(result), mode: 'new', emailRequested });
          return undefined;
        } catch (error) {
          return this.fail(error, field);
        }
      });
    } finally {
      this.saving.set(false);
    }
  }

  private fail(error: unknown, field: typeof this.inviteForm) {
    const problem = problemOf(error);
    if (problem?.type === PROBLEM.validationFailed) {
      const roleErrors = problem.errors.filter(
        (e) => e.field === 'roles' || e.field.startsWith('roles'),
      );
      if (roleErrors.length > 0) {
        this.rolesError.set(roleErrors[0].message);
      }
      const errors = problem.errors
        .filter((e) => e.field === 'email')
        .map((e) => ({ fieldTree: field.email, kind: 'server', message: e.message }));
      if (errors.length > 0 || roleErrors.length > 0) {
        return errors;
      }
    }
    if (problem?.type === PROBLEM.userActive && problem.userId) {
      this.conflict.set({ kind: 'user-active', userId: problem.userId });
    } else if (problem?.type === PROBLEM.invitationExists) {
      this.conflict.set({ kind: 'invitation-exists' });
    } else if (problem?.type === PROBLEM.emailUnavailable) {
      this.emailUnavailable.set(true);
    } else if (problem?.status !== 403) {
      this.failure.set(true);
    }
    return undefined;
  }
}
