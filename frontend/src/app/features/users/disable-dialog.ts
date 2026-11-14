import { Component, computed, inject, signal } from '@angular/core';
import { FormField, form, required, submit, validate } from '@angular/forms/signals';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import {
  HlmDialogDescription,
  HlmDialogFooter,
  HlmDialogHeader,
  HlmDialogTitle,
} from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSelectImports } from '@spartan-ng/helm/select';
import { DisableReason } from '../../core/api/models/disable-reason';
import { DISABLE_REASON } from '../../core/api/models/disable-reason-array';
import { DisableRequest } from '../../core/api/models/disable-request';
import { UserDetails } from '../../core/api/models/user-details';
import { PROBLEM, problemOf } from '../../core/problem';
import { ToastService } from '../../shared/toast/toast.service';
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
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogDescription,
    HlmDialogFooter,
    HlmButton,
    HlmInput,
    HlmLabel,
    HlmSelectImports,
  ],
  template: `
    <div class="flex flex-col gap-5">
      <div hlmDialogHeader>
        <h2 hlmDialogTitle class="font-display text-lg font-semibold tracking-tight">
          Disable {{ data.name }}?
        </h2>
        <p hlmDialogDescription>They lose access on their next action.</p>
      </div>

      <form novalidate (submit)="onSubmit($event)" class="space-y-4">
        <div class="flex flex-col gap-2.5">
          <label hlmLabel for="disable-reason">Reason</label>
          <hlm-select
            [value]="disableForm.reason().value()"
            [itemToString]="reasonLabel"
            (valueChange)="setReason($event)"
          >
            <hlm-select-trigger
              buttonId="disable-reason"
              class="w-full"
              [forceInvalid]="disableForm.reason().touched() && disableForm.reason().invalid()"
              [attr.aria-invalid]="
                disableForm.reason().touched() && disableForm.reason().invalid() ? 'true' : null
              "
              [attr.aria-describedby]="
                disableForm.reason().touched() && disableForm.reason().errors().length > 0
                  ? 'disable-reason-error'
                  : null
              "
            >
              <hlm-select-value placeholder="Select a reason" />
            </hlm-select-trigger>
            <hlm-select-content *hlmSelectPortal>
              @for (reason of reasons; track reason) {
                <hlm-select-item [value]="reason">{{ labels[reason] }}</hlm-select-item>
              }
            </hlm-select-content>
          </hlm-select>
          @if (disableForm.reason().touched() && disableForm.reason().errors().length > 0) {
            <div
              id="disable-reason-error"
              class="space-y-0.5 text-xs text-destructive"
              role="alert"
            >
              @for (error of disableForm.reason().errors(); track error.message) {
                <p>{{ error.message }}</p>
              }
            </div>
          }
        </div>

        <div class="flex flex-col gap-2.5">
          <label hlmLabel for="disable-comment">{{ commentLabel() }}</label>
          <textarea
            hlmInput
            id="disable-comment"
            rows="3"
            class="h-auto min-h-20 py-2"
            [formField]="disableForm.comment"
            [forceInvalid]="disableForm.comment().touched() && disableForm.comment().invalid()"
            [attr.aria-invalid]="
              disableForm.comment().touched() && disableForm.comment().invalid() ? 'true' : null
            "
            [attr.aria-describedby]="
              disableForm.comment().touched() && disableForm.comment().errors().length > 0
                ? 'disable-comment-count disable-comment-error'
                : 'disable-comment-count'
            "
          ></textarea>
          <div class="flex items-start justify-between gap-2 text-xs">
            @if (disableForm.comment().touched() && disableForm.comment().errors().length > 0) {
              <div id="disable-comment-error" class="space-y-0.5 text-destructive" role="alert">
                @for (error of disableForm.comment().errors(); track error.message) {
                  <p>{{ error.message }}</p>
                }
              </div>
            } @else {
              <span></span>
            }
            <span id="disable-comment-count" class="font-mono text-muted-foreground">
              {{ length() }} / {{ limit }}
            </span>
          </div>
        </div>

        <div hlmDialogFooter>
          <button hlmBtn variant="outline" type="button" (click)="ref.close()">Cancel</button>
          <button
            hlmBtn
            type="submit"
            class="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            [disabled]="saving()"
          >
            Disable
          </button>
        </div>
      </form>
    </div>
  `,
})
export class DisableDialog {
  protected readonly data = injectBrnDialogContext<DisableDialogData>();
  protected readonly ref = inject<BrnDialogRef<UserDetails>>(BrnDialogRef);
  private readonly toast = inject(ToastService);

  protected readonly reasons = DISABLE_REASON;
  protected readonly labels = REASON_LABELS;
  protected readonly limit = COMMENT_LIMIT;
  protected readonly saving = signal(false);

  private readonly model = signal({ reason: '', comment: '' });
  protected readonly disableForm = form(this.model, (path) => {
    required(path.reason, { message: 'Choose a reason' });
    required(path.comment, {
      when: (field) => field.valueOf(path.reason) === 'OTHER',
      message: 'Enter a comment',
    });
    validate(path.comment, (field) => {
      const text = field.value();
      if (text.length > 0 && text.trim() === '' && field.valueOf(path.reason) === 'OTHER') {
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
  protected readonly reasonLabel = (reason: string): string =>
    REASON_LABELS[reason as DisableReason] ?? reason;

  protected setReason(value: string | null | undefined): void {
    this.model.update((current) => ({ ...current, reason: value ?? '' }));
  }

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
      this.toast.show("You can't lock yourself out.", { politeness: 'assertive' });
    } else if (problem?.type === PROBLEM.lastAdmin) {
      this.toast.show('MentorHub must keep at least one active administrator.', {
        politeness: 'assertive',
      });
    } else if (problem?.status !== 403) {
      this.toast.show('Could not disable the user. Try again.');
    }
    return undefined;
  }
}
