import { Component, computed, inject, signal } from '@angular/core';
import { FormField, form, required, submit, validate } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { DISABLE_REASON } from '../../core/api/models/disable-reason-array';
import { DisableRequest } from '../../core/api/models/disable-request';
import { UserDetails } from '../../core/api/models/user-details';
import { PROBLEM, problemOf } from '../../core/problem';
import { REASON_LABELS } from './labels';

export interface DisableDialogData {
  name: string;
  /** Sends the request; resolves with the updated user. */
  save: (request: DisableRequest) => Promise<UserDetails>;
}

const COMMENT_LIMIT = 500;

@Component({
  selector: 'app-disable-dialog',
  imports: [
    FormField,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>Disable {{ data.name }}?</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content>
        <p>They lose access on their next action.</p>
        <mat-form-field class="full">
          <mat-label>Reason</mat-label>
          <mat-select [formField]="disableForm.reason">
            @for (reason of reasons; track reason) {
              <mat-option [value]="reason">{{ labels[reason] }}</mat-option>
            }
          </mat-select>
          @for (error of disableForm.reason().errors(); track error.message) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field class="full">
          <mat-label>{{ commentLabel() }}</mat-label>
          <textarea matInput rows="3" [formField]="disableForm.comment"></textarea>
          <mat-hint align="end">{{ length() }} / {{ limit }}</mat-hint>
          @for (error of disableForm.comment().errors(); track error.message) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button matButton type="button" mat-dialog-close>Cancel</button>
        <button matButton="filled" type="submit" [disabled]="saving()">Disable</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .full {
      width: 100%;
    }
  `,
})
export class DisableDialog {
  protected readonly data = inject<DisableDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<DisableDialog, UserDetails>>(MatDialogRef);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly reasons = DISABLE_REASON;
  protected readonly labels = REASON_LABELS;
  protected readonly limit = COMMENT_LIMIT;
  protected readonly saving = signal(false);

  private readonly model = signal({ reason: '', comment: '' });
  protected readonly disableForm = form(this.model, (path) => {
    required(path.reason, { message: 'Choose a reason' });
    required(path.comment, {
      when: ({ valueOf }) => valueOf(path.reason) === 'OTHER',
      message: 'Enter a comment',
    });
    validate(path.comment, ({ value, valueOf }) => {
      const text = value();
      if (text.length > 0 && text.trim() === '' && valueOf(path.reason) === 'OTHER') {
        return { kind: 'required', message: 'Enter a comment' };
      }
      return text.trim().length > COMMENT_LIMIT
        ? { kind: 'maxLength', message: `Use at most ${COMMENT_LIMIT} characters` }
        : undefined;
    });
  });

  protected readonly length = computed(() => this.model().comment.trim().length);
  protected readonly commentLabel = computed(() =>
    this.model().reason === 'OTHER' ? 'Comment (required)' : 'Comment',
  );

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    try {
      await submit(this.disableForm, async (field) => {
        const { reason, comment } = field().value();
        const trimmed = comment.trim();
        try {
          const user = await this.data.save({
            reason: reason as DisableRequest['reason'],
            ...(trimmed ? { comment: trimmed } : {}),
          });
          this.ref.close(user);
          return undefined;
        } catch (error) {
          return this.fail(error, field);
        }
      });
    } finally {
      this.saving.set(false);
    }
  }

  private fail(error: unknown, field: typeof this.disableForm) {
    const problem = problemOf(error);
    if (problem?.type === PROBLEM.validationFailed) {
      const errors = problem.errors.flatMap((e) =>
        e.field === 'reason' || e.field === 'comment'
          ? [{ fieldTree: field[e.field], kind: 'server', message: e.message }]
          : [],
      );
      if (errors.length > 0) {
        return errors;
      }
    }
    if (problem?.type === PROBLEM.selfLockout) {
      this.snackBar.open("You can't lock yourself out.", 'Dismiss', { politeness: 'assertive' });
    } else if (problem?.type === PROBLEM.lastAdmin) {
      this.snackBar.open('MentorHub must keep at least one active administrator.', 'Dismiss', {
        politeness: 'assertive',
      });
    } else if (problem?.status !== 403) {
      this.snackBar.open('Could not disable the user. Try again.', 'Dismiss');
    }
    return undefined;
  }
}
