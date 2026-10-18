import { Component, inject, input, linkedSignal, output, signal } from '@angular/core';
import { FormField, form, submit, validate } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ProfileUpdate } from '../../core/api/models/profile-update';
import { PROBLEM, problemOf } from '../../core/problem';

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
  imports: [FormField, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <form novalidate (submit)="onSubmit($event)">
      <p class="email"><span class="label">Email</span> {{ values().email }}</p>
      <div class="fields">
        <mat-form-field>
          <mat-label>First name</mat-label>
          <input matInput autocomplete="given-name" [formField]="profileForm.firstName" />
          @for (error of profileForm.firstName().errors(); track error.message) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Last name</mat-label>
          <input matInput autocomplete="family-name" [formField]="profileForm.lastName" />
          @for (error of profileForm.lastName().errors(); track error.message) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Phone</mat-label>
          <input matInput type="tel" autocomplete="tel" [formField]="profileForm.phone" />
          <mat-hint>For example +7 701 123 45 67</mat-hint>
          @for (error of profileForm.phone().errors(); track error.message) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>
      </div>
      <div class="actions">
        @if (cancellable()) {
          <button matButton type="button" (click)="cancelled.emit()">Cancel</button>
        }
        <button matButton="filled" type="submit" [disabled]="saving()">Save</button>
      </div>
    </form>
  `,
  styles: `
    .email {
      margin: 0 0 16px;
    }
    .label {
      color: var(--mat-sys-on-surface-variant);
      margin-inline-end: 8px;
    }
    .fields {
      display: flex;
      flex-wrap: wrap;
      gap: 0 16px;
    }
    mat-form-field {
      flex: 1 1 200px;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
    }
  `,
})
export class ProfileForm {
  private readonly snackBar = inject(MatSnackBar);

  readonly values = input.required<ProfileValues>();
  /** Stores the profile and returns what the server saved. */
  readonly save = input.required<(update: ProfileUpdate) => Promise<ProfileValues>>();
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
            this.snackBar.open('Could not save the profile. Try again.', 'Dismiss');
          }
          return undefined;
        }
      });
    } finally {
      this.saving.set(false);
    }
  }
}
