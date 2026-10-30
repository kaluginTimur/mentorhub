import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormField, form, required, submit, validate } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ClientConfigService } from '../../core/client-config.service';
import { Api } from '../../core/api/api';
import { createInvitation } from '../../core/api/fn/invitations/create-invitation';
import { InvitationLink } from '../../core/api/models/invitation-link';
import { CreatedInvitation } from '../../core/api/models/created-invitation';
import { Role } from '../../core/api/models/role';
import { ROLE } from '../../core/api/models/role-array';
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
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
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
      <h2 mat-dialog-title>Invite a person</h2>
      <form novalidate (submit)="onSubmit($event)">
        <mat-dialog-content>
          <mat-form-field class="full">
            <mat-label>Email</mat-label>
            <input matInput type="email" autocomplete="off" [formField]="inviteForm.email" />
            @for (error of inviteForm.email().errors(); track error.message) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>

          <fieldset aria-describedby="roles-hint">
            <legend>Roles</legend>
            @for (role of roles; track role) {
              <mat-checkbox
                [checked]="selected().includes(role)"
                (change)="toggle(role, $event.checked)"
              >
                {{ labels[role] }}
              </mat-checkbox>
            }
            <p class="hint" id="roles-hint">
              Without roles the person can sign in but has no access to anything.
            </p>
            @if (rolesError(); as message) {
              <p class="error" role="alert">{{ message }}</p>
            }
          </fieldset>

          @if (emailEnabled) {
            <mat-checkbox
              class="send-email"
              [checked]="sendEmail()"
              (change)="sendEmail.set($event.checked)"
            >
              Send the invitation by email to this address
            </mat-checkbox>
          }

          @if (conflict(); as c) {
            <div class="conflict" role="alert">
              @if (c.kind === 'user-active') {
                <span>This person already has access.</span>
                <a matButton [routerLink]="['/users', c.userId]" (click)="ref.close()">Open user</a>
              } @else {
                <span>There is already an invitation for this email.</span>
                <a
                  matButton
                  routerLink="/invitations"
                  [queryParams]="{ q: trimmedEmail() }"
                  (click)="ref.close()"
                  >Show invitation</a
                >
              }
            </div>
          }
          @if (emailUnavailable()) {
            <p class="error" role="alert">{{ emailUnavailableText }}</p>
          }
          @if (failure()) {
            <p class="error" role="alert">Something went wrong. Try again.</p>
          }
        </mat-dialog-content>
        <mat-dialog-actions align="end">
          <button matButton type="button" mat-dialog-close>Cancel</button>
          <button matButton="filled" type="submit" [disabled]="saving()">Create invitation</button>
        </mat-dialog-actions>
      </form>
    }
  `,
  styles: `
    .full {
      width: 100%;
    }
    fieldset {
      border: 0;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
    }
    legend {
      font: var(--mat-sys-title-small);
      padding: 0 0 4px;
    }
    .send-email {
      display: block;
      margin-top: 8px;
    }
    .hint {
      margin: 4px 0 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
    .error {
      margin: 8px 0 0;
      color: var(--mat-sys-error);
      font: var(--mat-sys-body-small);
    }
    .conflict {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
      margin-top: 12px;
      color: var(--mat-sys-error);
    }
  `,
})
export class InviteDialog {
  private readonly api = inject(Api);
  private readonly data = inject<InviteDialogData | null>(MAT_DIALOG_DATA, { optional: true });
  protected readonly ref = inject<MatDialogRef<InviteDialog>>(MatDialogRef);

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
