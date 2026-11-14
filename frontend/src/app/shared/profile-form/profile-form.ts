import { Component, inject, input, linkedSignal, output, signal } from '@angular/core';
import { FormField, form, submit, validate } from '@angular/forms/signals';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { ProfileUpdate } from '../../core/api/models/profile-update';
import { PROBLEM, problemOf } from '../../core/problem';
import { ToastService } from '../toast/toast.service';

export interface ProfileValues {
  email: string;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
}

const NAME_LIMIT = 100;
const NAME_MESSAGE = `Use at most ${NAME_LIMIT} characters`;
const PHONE_MESSAGE = 'Enter a valid phone number';
const FIELDS = ['firstName', 'lastName', 'phone'] as const;

const blankToNull = (value: string): string | null => (value.trim() === '' ? null : value.trim());

/** First name, last name and phone of a user; shared by the user page and "My profile". */
@Component({
  selector: 'app-profile-form',
  imports: [FormField, HlmInput, HlmLabel, HlmButton],
  template: `
    <form novalidate (submit)="onSubmit($event)" class="space-y-5">
      @if (showEmail()) {
        <div
          class="flex flex-wrap items-baseline gap-2 rounded-lg bg-muted/50 px-3.5 py-2.5 text-sm"
        >
          <span class="font-medium text-muted-foreground">Email</span>
          <span class="font-mono text-foreground">{{ values().email }}</span>
        </div>
      }

      <div class="grid gap-4 sm:grid-cols-2">
        <div class="flex flex-col gap-2.5">
          <label hlmLabel for="profile-first-name">First name</label>
          <input
            hlmInput
            id="profile-first-name"
            autocomplete="given-name"
            [formField]="profileForm.firstName"
            [forceInvalid]="profileForm.firstName().touched() && profileForm.firstName().invalid()"
            [attr.aria-invalid]="
              profileForm.firstName().touched() && profileForm.firstName().invalid() ? 'true' : null
            "
            [attr.aria-describedby]="
              profileForm.firstName().touched() && profileForm.firstName().errors().length > 0
                ? 'profile-first-name-error'
                : null
            "
          />
          @if (profileForm.firstName().touched() && profileForm.firstName().errors().length > 0) {
            <div
              id="profile-first-name-error"
              class="space-y-0.5 text-xs text-destructive"
              role="alert"
            >
              @for (error of profileForm.firstName().errors(); track error.message) {
                <p>{{ error.message }}</p>
              }
            </div>
          }
        </div>

        <div class="flex flex-col gap-2.5">
          <label hlmLabel for="profile-last-name">Last name</label>
          <input
            hlmInput
            id="profile-last-name"
            autocomplete="family-name"
            [formField]="profileForm.lastName"
            [forceInvalid]="profileForm.lastName().touched() && profileForm.lastName().invalid()"
            [attr.aria-invalid]="
              profileForm.lastName().touched() && profileForm.lastName().invalid() ? 'true' : null
            "
            [attr.aria-describedby]="
              profileForm.lastName().touched() && profileForm.lastName().errors().length > 0
                ? 'profile-last-name-error'
                : null
            "
          />
          @if (profileForm.lastName().touched() && profileForm.lastName().errors().length > 0) {
            <div
              id="profile-last-name-error"
              class="space-y-0.5 text-xs text-destructive"
              role="alert"
            >
              @for (error of profileForm.lastName().errors(); track error.message) {
                <p>{{ error.message }}</p>
              }
            </div>
          }
        </div>

        <div class="flex flex-col gap-2.5 sm:col-span-2">
          <label hlmLabel for="profile-phone">Phone</label>
          <input
            hlmInput
            id="profile-phone"
            type="tel"
            autocomplete="tel"
            [formField]="profileForm.phone"
            [forceInvalid]="profileForm.phone().touched() && profileForm.phone().invalid()"
            [attr.aria-invalid]="
              profileForm.phone().touched() && profileForm.phone().invalid() ? 'true' : null
            "
            [attr.aria-describedby]="
              profileForm.phone().touched() && profileForm.phone().errors().length > 0
                ? 'profile-phone-hint profile-phone-error'
                : 'profile-phone-hint'
            "
          />
          <p id="profile-phone-hint" class="text-xs text-muted-foreground">
            For example +7 701 123 45 67
          </p>
          @if (profileForm.phone().touched() && profileForm.phone().errors().length > 0) {
            <div id="profile-phone-error" class="space-y-0.5 text-xs text-destructive" role="alert">
              @for (error of profileForm.phone().errors(); track error.message) {
                <p>{{ error.message }}</p>
              }
            </div>
          }
        </div>
      </div>

      <div class="flex justify-end gap-2">
        @if (cancellable()) {
          <button hlmBtn variant="outline" type="button" (click)="cancelled.emit()">Cancel</button>
        }
        <button hlmBtn type="submit" [disabled]="saving()">Save</button>
      </div>
    </form>
  `,
})
export class ProfileForm {
  private readonly toast = inject(ToastService);

  readonly values = input.required<ProfileValues>();
  /** Stores the profile and returns what the server saved. */
  readonly save = input.required<(update: ProfileUpdate) => Promise<ProfileValues>>();
  readonly showEmail = input(true);
  readonly cancellable = input(false);
  readonly saved = output<ProfileValues>();
  readonly cancelled = output();

  protected readonly saving = signal(false);
  private readonly model = linkedSignal(() => ({
    firstName: this.values().firstName ?? '',
    lastName: this.values().lastName ?? '',
    phone: this.values().phone ?? '',
  }));

  protected readonly profileForm = form(this.model, (path) => {
    for (const name of [path.firstName, path.lastName]) {
      validate(name, ({ value }) =>
        value().trim().length > NAME_LIMIT
          ? { kind: 'maxLength', message: NAME_MESSAGE }
          : undefined,
      );
    }
  });

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.saving.set(true);
    try {
      await submit(this.profileForm, async (field) => {
        const { firstName, lastName, phone } = field().value();
        try {
          const stored = await this.save()({
            firstName: blankToNull(firstName),
            lastName: blankToNull(lastName),
            phone: blankToNull(phone),
          });
          this.saved.emit(stored);
          return undefined;
        } catch (error) {
          const problem = problemOf(error);
          if (problem?.type === PROBLEM.validationFailed) {
            const errors = problem.errors.flatMap((e) => {
              const name = FIELDS.find((f) => f === e.field);
              return name
                ? [
                    {
                      fieldTree: field[name],
                      kind: 'server',
                      message: name === 'phone' ? PHONE_MESSAGE : e.message,
                    },
                  ]
                : [];
            });
            if (errors.length > 0) {
              return errors;
            }
          }
          if (problem?.status !== 403) {
            this.toast.show('Could not save the profile. Try again.');
          }
          return undefined;
        }
      });
    } finally {
      this.saving.set(false);
    }
  }
}
