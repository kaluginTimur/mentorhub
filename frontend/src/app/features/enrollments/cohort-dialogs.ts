import { Component, computed, inject, signal } from '@angular/core';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import {
  HlmDialogDescription,
  HlmDialogFooter,
  HlmDialogHeader,
  HlmDialogService,
  HlmDialogTitle,
} from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { CohortCandidate } from '../../core/api/models/cohort-candidate';
import { DateField } from '../../shared/date-field/date-field';
import { CandidateLoader, CohortCandidatePicker } from './cohort-candidate-picker';
import { firstValueFrom } from 'rxjs';

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

export interface MoveConfirmData {
  cohortName: string;
  moving: CohortCandidate[];
  /** Chosen students who are in no cohort and are only added. */
  added: number;
}

/** The confirmation of COH-8.1: who leaves which cohort. Closes with true to go on. */
@Component({
  selector: 'app-move-confirm-dialog',
  imports: [HlmDialogHeader, HlmDialogTitle, HlmDialogDescription, HlmDialogFooter, HlmButton],
  host: { class: 'block min-w-0' },
  template: `
    <div class="flex flex-col gap-4" data-testid="move-confirm-dialog">
      <div hlmDialogHeader>
        <h2
          hlmDialogTitle
          class="pe-10 font-display text-lg font-semibold tracking-tight break-words"
        >
          Move {{ countText }} into {{ data.cohortName }}?
        </h2>
        <p hlmDialogDescription>Saving takes them out of their current cohorts.</p>
      </div>
      <div
        role="region"
        aria-label="Students to move"
        tabindex="0"
        data-testid="move-list"
        class="max-h-60 overflow-y-auto rounded-md border border-border px-3 py-1 outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        <ul class="divide-y divide-border text-sm">
          @for (c of data.moving; track c.id) {
            <li data-testid="move-item" class="py-2 break-words">
              <b class="font-medium">{{ c.student.name }}</b> leaves
              <b class="font-medium">{{ c.cohort?.name }}</b>
            </li>
          }
        </ul>
      </div>
      @if (data.added > 0) {
        <p class="text-sm text-muted-foreground">
          {{ data.added }} more {{ data.added === 1 ? 'student has' : 'students have' }} no cohort
          and {{ data.added === 1 ? 'is' : 'are' }} only added.
        </p>
      }
      <div hlmDialogFooter>
        <button hlmBtn variant="outline" type="button" (click)="ref.close(false)">Cancel</button>
        <button hlmBtn type="button" data-testid="move-confirm" (click)="ref.close(true)">
          Move and save
        </button>
      </div>
    </div>
  `,
})
export class MoveConfirmDialog {
  protected readonly data = injectBrnDialogContext<MoveConfirmData>();
  protected readonly ref = inject<BrnDialogRef<boolean>>(BrnDialogRef);
  protected readonly countText = plural(this.data.moving.length, 'student', 'students');
}

export interface CohortFormValue {
  name: string;
  startDate: string;
  candidates: CohortCandidate[];
}

export interface CohortFormErrors {
  name?: string;
  startDate?: string;
}

export interface CohortStudentsDialogData {
  /** `create` asks for a name and a start date too. */
  mode: 'create' | 'add';
  /** The cohort the students are added to (`add`). */
  cohortName?: string;
  load: CandidateLoader;
  /** True: close (done, or refused and explained); false: stay open; or the field errors to show. */
  save: (value: CohortFormValue) => Promise<boolean | CohortFormErrors>;
}

/** "Create cohort" and "Add students": one form around the candidate picker. */
@Component({
  selector: 'app-cohort-students-dialog',
  imports: [
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogDescription,
    HlmDialogFooter,
    HlmButton,
    HlmInput,
    HlmLabel,
    CohortCandidatePicker,
    DateField,
  ],
  host: { class: 'block min-w-0' },
  template: `
    <div
      class="flex min-w-0 flex-col gap-5"
      [attr.data-testid]="isCreate ? 'create-cohort-dialog' : 'add-students-dialog'"
    >
      <div hlmDialogHeader>
        <h2
          hlmDialogTitle
          class="pe-10 font-display text-lg font-semibold tracking-tight break-words"
        >
          {{ isCreate ? 'Create cohort' : 'Add students to ' + data.cohortName }}
        </h2>
        @if (isCreate) {
          <p hlmDialogDescription>
            Name the intake, give its start date and choose the students to add.
          </p>
        } @else {
          <p hlmDialogDescription>Students already in this cohort are not listed.</p>
        }
      </div>

      <form novalidate class="flex min-w-0 flex-col gap-5" (submit)="onSubmit($event)">
        @if (isCreate) {
          <div class="grid gap-4 sm:grid-cols-[1fr_11rem]">
            <div class="flex min-w-0 flex-col gap-2">
              <label hlmLabel for="cohort-name">Name</label>
              <input
                hlmInput
                id="cohort-name"
                type="text"
                autocomplete="off"
                data-testid="cohort-name"
                [value]="name()"
                [attr.aria-invalid]="nameError() ? 'true' : null"
                [attr.aria-describedby]="nameError() ? 'cohort-name-error' : null"
                (input)="name.set($any($event.target).value); serverName.set('')"
              />
              @if (nameError(); as message) {
                <p id="cohort-name-error" class="text-xs text-destructive">{{ message }}</p>
              }
            </div>
            <div class="flex min-w-0 flex-col gap-2">
              <label hlmLabel for="cohort-start-date">Start date</label>
              <app-date-field
                inputId="cohort-start-date"
                inputTestId="cohort-start-date"
                [invalid]="!!dateError()"
                [describedBy]="dateError() ? 'cohort-start-date-error' : undefined"
                [value]="startDate()"
                (valueChange)="startDate.set($event); serverDate.set('')"
              />
              @if (dateError(); as message) {
                <p id="cohort-start-date-error" class="text-xs text-destructive">{{ message }}</p>
              }
            </div>
          </div>
          <fieldset class="flex min-w-0 flex-col gap-2">
            <legend class="mb-2 text-sm font-medium">
              Students <span class="font-normal text-muted-foreground">(optional)</span>
            </legend>
            <app-cohort-candidate-picker
              [load]="data.load"
              [chosen]="chosen()"
              (toggled)="toggle($event)"
              (refused)="ref.close(false)"
            />
          </fieldset>
        } @else {
          <app-cohort-candidate-picker
            [load]="data.load"
            [chosen]="chosen()"
            (toggled)="toggle($event)"
            (refused)="ref.close(false)"
          />
        }

        <div hlmDialogFooter class="items-center">
          @if (!isCreate && chosen().size === 0) {
            <span
              id="add-students-hint"
              class="text-sm text-muted-foreground max-sm:w-full sm:me-auto"
              >Choose at least one student.</span
            >
          }
          <button hlmBtn variant="outline" type="button" (click)="ref.close(false)">Cancel</button>
          <button
            hlmBtn
            type="submit"
            [attr.data-testid]="isCreate ? 'create-cohort-submit' : 'add-students-submit'"
            [attr.aria-disabled]="notReady() || saving() ? 'true' : null"
            [attr.aria-describedby]="notReady() ? 'add-students-hint' : null"
          >
            {{ submitLabel() }}
          </button>
        </div>
      </form>
    </div>
  `,
})
export class CohortStudentsDialog {
  protected readonly data = injectBrnDialogContext<CohortStudentsDialogData>();
  protected readonly ref = inject<BrnDialogRef<boolean>>(BrnDialogRef);
  private readonly dialogs = inject(HlmDialogService);

  protected readonly isCreate = this.data.mode === 'create';
  protected readonly name = signal('');
  protected readonly startDate = signal('');
  protected readonly chosen = signal<ReadonlyMap<string, CohortCandidate>>(new Map());
  protected readonly saving = signal(false);
  private readonly submitted = signal(false);
  protected readonly serverName = signal('');
  protected readonly serverDate = signal('');

  protected readonly nameError = computed(
    () =>
      this.serverName() ||
      (this.submitted() && this.name().trim() === '' ? 'Enter a name for the cohort.' : ''),
  );
  protected readonly dateError = computed(
    () =>
      this.serverDate() ||
      (this.submitted() && this.startDate() === '' ? 'Choose a start date.' : ''),
  );
  protected readonly notReady = computed(() => !this.isCreate && this.chosen().size === 0);
  protected readonly submitLabel = computed(() => {
    if (this.isCreate) {
      return 'Create cohort';
    }
    const n = this.chosen().size;
    return n === 0 ? 'Add students' : `Add ${plural(n, 'student', 'students')}`;
  });

  protected toggle(candidate: CohortCandidate): void {
    const next = new Map(this.chosen());
    if (!next.delete(candidate.id)) {
      next.set(candidate.id, candidate);
    }
    this.chosen.set(next);
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.saving() || this.notReady()) {
      return;
    }
    this.submitted.set(true);
    if (this.isCreate && (this.name().trim() === '' || this.startDate() === '')) {
      return;
    }
    const candidates = [...this.chosen().values()];
    const moving = candidates.filter((c) => c.cohort !== null);
    if (moving.length > 0) {
      const confirm = this.dialogs.open<boolean>(MoveConfirmDialog, {
        contentClass: 'sm:max-w-[460px]',
        context: {
          cohortName: this.isCreate ? this.name().trim() : (this.data.cohortName ?? ''),
          moving,
          added: candidates.length - moving.length,
        } satisfies MoveConfirmData,
      });
      if (!(await firstValueFrom(confirm.closed$))) {
        return;
      }
    }
    this.saving.set(true);
    try {
      const result = await this.data.save({
        name: this.name().trim(),
        startDate: this.startDate(),
        candidates,
      });
      if (result === true) {
        this.ref.close(true);
      } else if (result !== false) {
        this.serverName.set(result.name ?? '');
        this.serverDate.set(result.startDate ?? '');
      }
    } finally {
      this.saving.set(false);
    }
  }
}
