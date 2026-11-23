import {
  Component,
  DestroyRef,
  ElementRef,
  PendingTasks,
  computed,
  inject,
  linkedSignal,
  resource,
  signal,
} from '@angular/core';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import type { BrnOverlayState } from '@spartan-ng/brain/overlay';
import { HlmAutocompleteImports } from '@spartan-ng/helm/autocomplete';
import { HlmButton } from '@spartan-ng/helm/button';
import {
  HlmDialogDescription,
  HlmDialogFooter,
  HlmDialogHeader,
  HlmDialogTitle,
} from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { Cohort } from '../../core/api/models/cohort';
import { EnrollmentCandidate } from '../../core/api/models/enrollment-candidate';
import { EnrollmentCandidatePage } from '../../core/api/models/enrollment-candidate-page';
import { DateField, parseIsoDate, toIsoDate } from '../../shared/date-field/date-field';
import { DATE_FORMAT } from '../../shared/date/date-format';
import { Email } from '../../shared/email/email';
import { DatePipe } from '@angular/common';

export const CANDIDATE_SEARCH_LIMIT = 100;
const SEARCH_DELAY = 300;

export interface EnrollStudentDialogData {
  /** Resolves null when the track refused and the page explained it; the dialog then closes. */
  load: (q: string) => Promise<EnrollmentCandidatePage | null>;
  /**
   * Enrolls the student. Resolves true when the dialog may close (done, or refused and explained),
   * false to stay open, or a message to show under the start date field.
   */
  enroll: (
    studentId: string,
    startDate: string,
    cohortId: string | null,
  ) => Promise<boolean | string>;
  /** The cohorts of the track, newest start first (ENR-7); none when the track has none. */
  cohorts?: Cohort[];
}

/** Today in the user's time zone, as `yyyy-mm-dd`. */
export function localToday(): string {
  return toIsoDate(new Date());
}

/** The dialog that enrolls a candidate on a track (ENR-7). */
@Component({
  selector: 'app-enroll-student-dialog',
  imports: [
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogDescription,
    HlmDialogFooter,
    HlmButton,
    HlmInput,
    HlmAutocompleteImports,
    Email,
    HlmLabel,
    DateField,
    DatePipe,
  ],
  template: `
    <div class="flex flex-col gap-5">
      <div hlmDialogHeader>
        <h2
          hlmDialogTitle
          class="pe-10 font-display text-lg font-semibold tracking-tight break-words"
        >
          Enroll a student
        </h2>
        <p hlmDialogDescription>
          Only active users with the student role and no active enrollment can be enrolled.
        </p>
      </div>

      <form novalidate (submit)="onSubmit($event)" class="space-y-4">
        <div class="flex flex-col gap-2.5">
          <label hlmLabel for="enroll-student-search">Student</label>
          <hlm-autocomplete
            [value]="selected()"
            [itemToString]="candidateName"
            [isItemEqualToValue]="sameCandidate"
            [state]="panelState()"
            (stateChanged)="panelState.set($event)"
            (valueChange)="onChoose($event)"
            (searchChange)="onSearch($event)"
          >
            <hlm-autocomplete-input
              inputId="enroll-student-search"
              placeholder="Search by name or email"
              showClear
              (click)="panelState.set('open')"
              [aria-invalid]="missing() ? true : undefined"
              [attr.aria-describedby]="missing() ? 'enroll-student-error' : 'enroll-student-chosen'"
            />
            <ng-template hlmAutocompletePortal>
              <hlm-autocomplete-content>
                @if (loaded(); as page) {
                  <div hlmAutocompleteList class="max-h-56">
                    @for (c of page.items; track c.id) {
                      <hlm-autocomplete-item [value]="c" class="items-start">
                        <span class="flex min-w-0 flex-col">
                          <span class="font-medium break-words">{{ c.name }}</span>
                          <span class="font-mono text-xs text-muted-foreground"
                            ><app-email [value]="c.email"
                          /></span>
                        </span>
                      </hlm-autocomplete-item>
                    }
                  </div>
                  <div hlmAutocompleteEmpty>No student matches the search.</div>
                  @if (page.totalItems > page.items.length) {
                    <p class="border-t border-border px-3 py-2 text-xs text-muted-foreground">
                      Showing {{ page.items.length }} of {{ page.totalItems }}. Type to narrow the
                      search.
                    </p>
                  }
                }
              </hlm-autocomplete-content>
            </ng-template>
          </hlm-autocomplete>

          <div class="h-1">
            @if (candidates.isLoading()) {
              <div
                role="progressbar"
                aria-label="Loading students"
                class="h-1 w-full overflow-hidden rounded-full bg-primary/15"
              >
                <div class="animate-mh-indeterminate h-full w-1/3 rounded-full bg-primary"></div>
              </div>
            }
          </div>

          @if (failed()) {
            <div class="flex flex-wrap items-center gap-3">
              <p role="alert" class="text-sm text-destructive">Could not load the students.</p>
              <button
                hlmBtn
                variant="outline"
                size="sm"
                type="button"
                (click)="candidates.reload()"
              >
                Retry
              </button>
            </div>
          }

          <!-- One line is reserved for the chosen student or the error, so the dialog keeps its size. -->
          <div class="min-h-4">
            <p
              id="enroll-student-chosen"
              class="text-xs text-muted-foreground"
              aria-live="polite"
              [class.hidden]="missing()"
            >
              @if (selected(); as s) {
                Chosen:
                <span class="font-mono text-foreground"><app-email [value]="s.email" /></span>
              }
            </p>
            @if (missing()) {
              <p id="enroll-student-error" class="text-xs text-destructive" role="alert">
                Choose a student
              </p>
            }
          </div>
        </div>

        <div class="grid gap-4 sm:grid-cols-2">
          <div class="flex flex-col gap-2.5">
            <label hlmLabel for="enroll-cohort">Cohort</label>
            <select
              hlmInput
              id="enroll-cohort"
              data-testid="enroll-cohort"
              [disabled]="cohorts.length === 0"
              [attr.aria-describedby]="cohorts.length === 0 ? 'enroll-cohort-hint' : null"
              (change)="cohortId.set($any($event.target).value)"
            >
              <option value="" [selected]="cohortId() === ''">No cohort</option>
              @for (c of cohorts; track c.id) {
                <option [value]="c.id" [selected]="cohortId() === c.id">
                  {{ c.name }} (starts {{ c.startDate | date: dateFormat }})
                </option>
              }
            </select>
            <div class="min-h-4">
              @if (cohorts.length === 0) {
                <p id="enroll-cohort-hint" class="text-xs text-muted-foreground">
                  This track has no cohorts yet.
                </p>
              }
            </div>
          </div>
          <div class="flex flex-col gap-2.5">
            <label hlmLabel for="enroll-start-date">Start date</label>
            <app-date-field
              inputId="enroll-start-date"
              [invalid]="!!dateError()"
              [describedBy]="dateError() ? 'enroll-start-date-error' : undefined"
              [value]="startDate()"
              (valueChange)="startDate.set($event); serverDateError.set('')"
            />
            <div class="min-h-4">
              @if (dateError(); as message) {
                <p id="enroll-start-date-error" class="text-xs text-destructive" role="alert">
                  {{ message }}
                </p>
              }
            </div>
          </div>
        </div>

        <div hlmDialogFooter>
          <button hlmBtn variant="outline" type="button" (click)="ref.close()">Cancel</button>
          <button hlmBtn type="submit" [disabled]="saving()">Enroll</button>
        </div>
      </form>
    </div>
  `,
})
export class EnrollStudentDialog {
  protected readonly data = injectBrnDialogContext<EnrollStudentDialogData>();
  protected readonly ref = inject<BrnDialogRef<boolean>>(BrnDialogRef);
  private readonly pending = inject(PendingTasks);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly panelState = signal<BrnOverlayState | null>(null);
  protected readonly candidateName = (c: EnrollmentCandidate) => c.name;
  protected readonly sameCandidate = (
    a: EnrollmentCandidate,
    b: EnrollmentCandidate | null | undefined,
  ) => a.id === b?.id;
  /** The search applied to the list, empty on open (the candidates load at once, the panel opens on a click): it follows the text 300 ms after typing stops. */
  protected readonly term = signal('');
  private searchTimer: ReturnType<typeof setTimeout> | undefined;
  private releaseSearch: (() => void) | undefined;

  protected readonly selected = signal<EnrollmentCandidate | null>(null);
  protected readonly cohorts = this.data.cohorts ?? [];
  protected readonly dateFormat = DATE_FORMAT;
  protected readonly cohortId = signal('');
  protected readonly startDate = signal(localToday());
  /** The typed text as a date for the calendar, or undefined while it is not a valid date. */
  protected readonly pickedDate = computed(() => parseIsoDate(this.startDate()));
  protected readonly saving = signal(false);
  private readonly submitted = signal(false);
  protected readonly serverDateError = signal('');

  protected readonly missing = computed(() => this.submitted() && this.selected() === null);
  protected readonly dateError = computed(() => {
    if (this.serverDateError()) {
      return this.serverDateError();
    }
    return this.submitted() && this.pickedDate() === undefined ? 'Choose a start date' : '';
  });

  protected readonly candidates = resource({
    params: () => this.term(),
    loader: async ({ params }): Promise<EnrollmentCandidatePage | null> => {
      const page = await this.data.load(params);
      if (page === null) {
        this.ref.close();
      }
      return page;
    },
  });
  protected readonly failed = computed(() => !!this.candidates.error());
  /** The last loaded page: it stays on screen while the next one loads. */
  protected readonly loaded = linkedSignal<
    EnrollmentCandidatePage | null | undefined,
    EnrollmentCandidatePage | null
  >({
    source: () => (this.candidates.hasValue() ? this.candidates.value() : undefined),
    computation: (page, previous) => page ?? previous?.value ?? null,
  });

  constructor() {
    // The candidates are requested on open, so the panel shows them at once.
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.searchTimer);
      this.releaseSearch?.();
    });
  }

  protected onSearch(value: string): void {
    // Typing after a choice drops the choice.
    if (this.selected() && value !== this.selected()?.name) {
      this.selected.set(null);
    }
    clearTimeout(this.searchTimer);
    this.releaseSearch ??= this.pending.add();
    this.searchTimer = setTimeout(() => {
      this.term.set(value.trim().slice(0, CANDIDATE_SEARCH_LIMIT));
      this.releaseSearch?.();
      this.releaseSearch = undefined;
    }, SEARCH_DELAY);
  }

  protected onChoose(candidate: EnrollmentCandidate | null | undefined): void {
    this.selected.set(candidate ?? null);
    if (!candidate) {
      this.panelState.set('open');
      // The clear button leaves the page when it is used: the focus returns to the field.
      this.host.nativeElement.querySelector<HTMLInputElement>('#enroll-student-search')?.focus();
    }
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.saving()) {
      return;
    }
    this.submitted.set(true);
    const student = this.selected();
    if (student === null || this.pickedDate() === undefined) {
      return;
    }
    this.saving.set(true);
    try {
      const result = await this.data.enroll(
        student.id,
        this.startDate().trim(),
        this.cohortId() || null,
      );
      if (result === true) {
        this.ref.close(true);
      } else if (typeof result === 'string') {
        this.serverDateError.set(result);
      }
    } finally {
      this.saving.set(false);
    }
  }
}
