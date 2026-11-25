import {
  Component,
  DestroyRef,
  PendingTasks,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  resource,
  signal,
} from '@angular/core';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCheckbox } from '@spartan-ng/helm/checkbox';
import { HlmInput } from '@spartan-ng/helm/input';
import { CohortCandidate } from '../../core/api/models/cohort-candidate';
import { CohortCandidatePage } from '../../core/api/models/cohort-candidate-page';
import { AssignmentMark } from '../tracks/assignment-mark';

export const PICKER_PAGE_SIZE = 10;
const SEARCH_DELAY = 300;
const SEARCH_LIMIT = 100;

/** Loads one page of candidates; resolves null when the track refused and the page explained it. */
export type CandidateLoader = (
  q: string,
  withoutCohort: boolean,
  page: number,
) => Promise<CohortCandidatePage | null>;

/** The students to put into a cohort: search, the "Only without cohort" switch, paging, choice across pages (COH-8). */
@Component({
  selector: 'app-cohort-candidate-picker',
  imports: [HlmInput, HlmButton, HlmCheckbox, AssignmentMark],
  host: { class: 'block min-w-0', 'data-testid': 'candidate-picker' },
  template: `
    <div class="flex min-w-0 flex-col gap-3">
      <div class="flex min-w-0 flex-wrap items-center gap-x-4 gap-y-2">
        <input
          hlmInput
          type="search"
          autocomplete="off"
          aria-label="Search students"
          placeholder="Search by name or email"
          data-testid="candidate-search"
          class="min-w-48 flex-1 basis-56"
          [value]="typed()"
          (input)="onSearch($any($event.target).value)"
        />
        <button
          type="button"
          role="switch"
          data-testid="only-without-cohort"
          class="inline-flex min-h-11 items-center gap-2 rounded-md text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50 md:min-h-9"
          [attr.aria-checked]="withoutCohort()"
          (click)="setWithoutCohort(!withoutCohort())"
        >
          <span
            aria-hidden="true"
            class="relative h-5 w-9 shrink-0 rounded-full border border-input transition-colors"
            [class]="withoutCohort() ? 'bg-primary' : 'bg-muted'"
          >
            <span
              class="absolute top-0.5 size-3.5 rounded-full bg-card shadow-sm transition-all"
              [class]="withoutCohort() ? 'left-[1.1rem]' : 'left-0.5 bg-muted-foreground'"
            ></span>
          </span>
          Only without cohort
        </button>
      </div>

      <div class="relative min-w-0" [attr.aria-busy]="candidates.isLoading()">
        <div class="absolute inset-x-0 -top-1.5 h-1">
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
        @if (candidates.error()) {
          <div class="flex flex-wrap items-center gap-3 py-2">
            <p role="alert" class="text-sm text-destructive">Could not load the students.</p>
            <button hlmBtn variant="outline" size="sm" type="button" (click)="candidates.reload()">
              Retry
            </button>
          </div>
        }
        @if (loaded(); as page) {
          @if (page.items.length > 0) {
            <ul
              class="max-h-72 min-w-0 divide-y divide-border overflow-y-auto rounded-md border border-border [scrollbar-gutter:stable]"
            >
              @for (c of page.items; track c.id) {
                <li>
                  <label
                    [for]="'candidate-' + c.id"
                    class="flex min-h-11 min-w-0 cursor-pointer items-center gap-3 ps-3 pe-4 py-2 hover:bg-muted/60 has-[:focus-visible]:bg-muted/60"
                  >
                    <hlm-checkbox
                      [inputId]="'candidate-' + c.id"
                      data-testid="candidate-checkbox"
                      [attr.data-enrollment-id]="c.id"
                      [aria-label]="'Choose ' + c.student.name"
                      [checked]="isChosen(c.id)"
                      (checkedChange)="toggled.emit(c)"
                    />
                    <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span class="flex flex-wrap items-center gap-x-2 gap-y-1">
                        <span class="font-medium [overflow-wrap:anywhere]">{{
                          c.student.name
                        }}</span>
                        @if (c.student.disabled) {
                          <app-assignment-mark label="Disabled" />
                        }
                        @if (!c.student.studentRole) {
                          <app-assignment-mark label="No student role" />
                        }
                      </span>
                      <span
                        class="block font-mono text-xs [overflow-wrap:anywhere] text-muted-foreground"
                        >{{ c.student.email }}</span
                      >
                    </span>
                    <span
                      data-testid="candidate-cohort"
                      class="ms-1 shrink-0 rounded-full border px-2 py-0.5 text-xs whitespace-nowrap"
                      [class]="
                        c.cohort
                          ? 'border-transparent bg-secondary text-secondary-foreground'
                          : 'border-border text-muted-foreground'
                      "
                      >{{ c.cohort ? 'In ' + c.cohort.name : 'No cohort' }}</span
                    >
                  </label>
                </li>
              }
            </ul>
          } @else if (!candidates.isLoading() && !candidates.error()) {
            <p
              data-testid="candidate-empty"
              class="rounded-md border border-dashed border-border px-3 py-4 text-sm text-muted-foreground"
            >
              {{ emptyText() }}
            </p>
          }
        }
      </div>

      <div class="flex min-w-0 flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <span role="status" data-testid="candidate-count" class="text-sm font-medium">{{
          countText()
        }}</span>
        @if (pages() > 1) {
          <nav
            aria-label="Candidate pages"
            data-testid="candidate-pager"
            class="flex items-center gap-2 text-sm text-muted-foreground"
          >
            <button
              hlmBtn
              variant="outline"
              size="sm"
              type="button"
              [disabled]="pageIndex() <= 0"
              (click)="pageIndex.set(pageIndex() - 1)"
            >
              Previous
            </button>
            <span>Page {{ pageIndex() + 1 }} of {{ pages() }}</span>
            <button
              hlmBtn
              variant="outline"
              size="sm"
              type="button"
              [disabled]="pageIndex() + 1 >= pages()"
              (click)="pageIndex.set(pageIndex() + 1)"
            >
              Next
            </button>
          </nav>
        }
      </div>
    </div>
  `,
})
export class CohortCandidatePicker {
  readonly load = input.required<CandidateLoader>();
  /** The chosen candidates by enrollment id; the owner of the choice changes it on `toggled`. */
  readonly chosen = input.required<ReadonlyMap<string, CohortCandidate>>();
  readonly toggled = output<CohortCandidate>();
  /** The track refused and the page explained it: the dialog should close. */
  readonly refused = output<void>();

  private readonly pending = inject(PendingTasks);
  private searchTimer: ReturnType<typeof setTimeout> | undefined;
  private releaseSearch: (() => void) | undefined;

  protected readonly typed = signal('');
  private readonly term = signal('');
  protected readonly withoutCohort = signal(true);
  protected readonly pageIndex = signal(0);

  protected readonly candidates = resource({
    params: () => ({ q: this.term(), only: this.withoutCohort(), page: this.pageIndex() }),
    loader: async ({ params }): Promise<CohortCandidatePage | null> => {
      const page = await this.load()(params.q, params.only, params.page);
      if (page === null) {
        this.refused.emit();
      }
      return page;
    },
  });
  /** The last loaded page: it stays on screen while the next one loads. */
  protected readonly loaded = linkedSignal<
    CohortCandidatePage | null | undefined,
    CohortCandidatePage | null
  >({
    source: () => (this.candidates.hasValue() ? this.candidates.value() : undefined),
    computation: (page, previous) => page ?? previous?.value ?? null,
  });

  protected readonly pages = computed(() => {
    const page = this.loaded();
    return page ? Math.max(1, Math.ceil(page.totalItems / PICKER_PAGE_SIZE)) : 1;
  });

  protected readonly countText = computed(() => {
    const total = this.chosen().size;
    const onPage = (this.loaded()?.items ?? []).filter((c) => this.chosen().has(c.id)).length;
    const elsewhere = total - onPage;
    return elsewhere > 0 && total > 0
      ? `${total} chosen (${onPage} on this page, ${elsewhere} on other pages)`
      : `${total} chosen`;
  });

  protected readonly emptyText = computed(() => {
    const q = this.term();
    if (q) {
      return `No students match "${q}". Check the spelling, or turn off "Only without cohort" to search the students of other cohorts too.`;
    }
    return this.withoutCohort()
      ? 'Every other student of this track is already in a cohort. Turn off "Only without cohort" to move students from their cohorts.'
      : 'There are no other students on this track.';
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.searchTimer);
      this.releaseSearch?.();
    });
  }

  protected isChosen(id: string): boolean {
    return this.chosen().has(id);
  }

  protected onSearch(value: string): void {
    this.typed.set(value);
    clearTimeout(this.searchTimer);
    this.releaseSearch ??= this.pending.add();
    this.searchTimer = setTimeout(() => {
      this.term.set(value.trim().slice(0, SEARCH_LIMIT));
      this.pageIndex.set(0);
      this.releaseSearch?.();
      this.releaseSearch = undefined;
    }, SEARCH_DELAY);
  }

  protected setWithoutCohort(value: boolean): void {
    this.withoutCohort.set(value);
    this.pageIndex.set(0);
  }
}
