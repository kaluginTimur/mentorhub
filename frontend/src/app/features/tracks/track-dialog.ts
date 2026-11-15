import { Component, inject, signal } from '@angular/core';
import { FormField, form, submit, validate } from '@angular/forms/signals';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogFooter, HlmDialogHeader, HlmDialogTitle } from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { Track } from '../../core/api/models/track';
import { TrackCreate } from '../../core/api/models/track-create';
import { PROBLEM, problemOf } from '../../core/problem';
import { ToastService } from '../../shared/toast/toast.service';
import { TRACK_CODE_LIMIT, TRACK_NAME_LIMIT } from './labels';

export interface TrackDialogData {
  title: string;
  confirmLabel: string;
  initial: TrackCreate;
  /** Sends the request; resolves with the saved track, or null when the change was refused and explained. */
  save: (body: TrackCreate) => Promise<Track | null>;
}

const textError = (value: string, limit: number, required: string) => {
  const text = value.trim();
  if (text === '') {
    return { kind: 'required', message: required };
  }
  return [...text].length > limit
    ? { kind: 'maxLength', message: `Use at most ${limit} characters` }
    : undefined;
};

@Component({
  selector: 'app-track-dialog',
  imports: [
    FormField,
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogFooter,
    HlmButton,
    HlmInput,
    HlmLabel,
  ],
  template: `
    <div class="flex flex-col gap-5">
      <div hlmDialogHeader>
        <h2
          hlmDialogTitle
          class="pe-10 font-display text-lg font-semibold tracking-tight break-words"
        >
          {{ data.title }}
        </h2>
      </div>

      <form novalidate (submit)="onSubmit($event)" class="space-y-4">
        <div class="flex flex-col gap-2.5">
          <label hlmLabel for="track-code">Code</label>
          <input
            hlmInput
            id="track-code"
            type="text"
            autocomplete="off"
            [formField]="trackForm.code"
            [forceInvalid]="trackForm.code().touched() && trackForm.code().invalid()"
            [attr.aria-invalid]="
              trackForm.code().touched() && trackForm.code().invalid() ? 'true' : null
            "
            [attr.aria-describedby]="
              trackForm.code().touched() && trackForm.code().errors().length > 0
                ? 'track-code-error'
                : null
            "
          />
          @if (trackForm.code().touched() && trackForm.code().errors().length > 0) {
            <div id="track-code-error" class="space-y-0.5 text-xs text-destructive" role="alert">
              @for (error of trackForm.code().errors(); track error.message) {
                <p>{{ error.message }}</p>
              }
            </div>
          }
        </div>

        <div class="flex flex-col gap-2.5">
          <label hlmLabel for="track-name">Name</label>
          <input
            hlmInput
            id="track-name"
            type="text"
            autocomplete="off"
            [formField]="trackForm.name"
            [forceInvalid]="trackForm.name().touched() && trackForm.name().invalid()"
            [attr.aria-invalid]="
              trackForm.name().touched() && trackForm.name().invalid() ? 'true' : null
            "
            [attr.aria-describedby]="
              trackForm.name().touched() && trackForm.name().errors().length > 0
                ? 'track-name-error'
                : null
            "
          />
          @if (trackForm.name().touched() && trackForm.name().errors().length > 0) {
            <div id="track-name-error" class="space-y-0.5 text-xs text-destructive" role="alert">
              @for (error of trackForm.name().errors(); track error.message) {
                <p>{{ error.message }}</p>
              }
            </div>
          }
        </div>

        <div hlmDialogFooter>
          <button hlmBtn variant="outline" type="button" (click)="ref.close()">Cancel</button>
          <button hlmBtn type="submit" [disabled]="saving()">{{ data.confirmLabel }}</button>
        </div>
      </form>
    </div>
  `,
})
export class TrackDialog {
  protected readonly data = injectBrnDialogContext<TrackDialogData>();
  protected readonly ref = inject<BrnDialogRef<Track>>(BrnDialogRef);
  private readonly toast = inject(ToastService);

  protected readonly saving = signal(false);

  private readonly model = signal({ code: this.data.initial.code, name: this.data.initial.name });
  protected readonly trackForm = form(this.model, (path) => {
    validate(path.code, (field) => textError(field.value(), TRACK_CODE_LIMIT, 'Enter a code'));
    validate(path.name, (field) => textError(field.value(), TRACK_NAME_LIMIT, 'Enter a name'));
  });

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    try {
      await submit(this.trackForm, async (field) => {
        const { code, name } = field().value();
        try {
          const track = await this.data.save({ code: code.trim(), name: name.trim() });
          this.ref.close(track ?? undefined);
          return undefined;
        } catch (error) {
          return this.fail(error, field);
        }
      });
    } finally {
      this.saving.set(false);
    }
  }

  private fail(error: unknown, field: typeof this.trackForm) {
    const problem = problemOf(error);
    if (problem?.type === PROBLEM.validationFailed) {
      const errors = problem.errors.flatMap((e) =>
        e.field === 'code' || e.field === 'name'
          ? [{ fieldTree: field[e.field], kind: 'server', message: e.message }]
          : [],
      );
      if (errors.length > 0) {
        return errors;
      }
    }
    if (problem?.status !== 403) {
      this.toast.dismissAll();
      this.toast.show('Could not save the track. Try again.');
    }
    return undefined;
  }
}
