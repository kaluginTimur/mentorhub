import {
  Component,
  DestroyRef,
  PendingTasks,
  computed,
  inject,
  linkedSignal,
  resource,
  signal,
} from '@angular/core';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCheckbox } from '@spartan-ng/helm/checkbox';
import {
  HlmDialogDescription,
  HlmDialogFooter,
  HlmDialogHeader,
  HlmDialogTitle,
} from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { MentorAssignmentCandidate } from '../../core/api/models/mentor-assignment-candidate';
import { MentorAssignmentCandidatePage } from '../../core/api/models/mentor-assignment-candidate-page';
import { PICKER_PAGE_SIZE } from './cohort-candidate-picker';
import { loadText, mentorsText } from './cohort-mentors-text';

const SEARCH_DELAY = 300;
const SEARCH_LIMIT = 100;

export interface AssignCohortMentorsDialogData {
  cohortName: string;
  /** Resolves null when the track refused and the opener explained it. */
  load: (q: string, page: number) => Promise<MentorAssignmentCandidatePage | null>;
  /** True: close (done, or refused and explained); false: stay open. */
  save: (userIds: string[]) => Promise<boolean>;
}

/** "Assign mentors" of a cohort: search, checkbox rows with the load, paging, the choice kept across pages (COH-8). */
@Component({
  selector: 'app-assign-cohort-mentors-dialog',
  imports: [
    HlmCheckbox,
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogDescription,
    HlmDialogFooter,
    HlmButton,
    HlmInput,
  ],
  host: { class: 'block min-w-0' },
  template: `
    <div class="flex min-w-0 flex-col gap-5" data-testid="assign-cohort-mentors-dialog">
      <div hlmDialogHeader>
        <h2
          hlmDialogTitle
          class="pe-10 font-display text-lg font-semibold tracking-tight break-words"
        >
          Assign mentors to {{ data.cohortName }}
        </h2>
        <p hlmDialogDescription>
          Mentors already in this cohort are not listed. Only active mentors of this track can be
          assigned.
        </p>
      </div>

      <form novalidate class="flex min-w-0 flex-col gap-5" (submit)="onSubmit($event)">
        <div class="flex min-w-0 flex-col gap-3">
          <input
            hlmInput
            type="search"
            autocomplete="off"
            aria-label="Search mentors"
            placeholder="Search by name or email"
            [value]="typed()"
            (input)="onSearch($any($event.target).value)"
          />

          <div class="relative min-w-0" [attr.aria-busy]="candidates.isLoading()">
            <div class="absolute inset-x-0 -top-1.5 h-1">
              @if (candidates.isLoading()) {
                <div
                  role="progressbar"
                  aria-label="Loading mentors"
                  class="h-1 w-full overflow-hidden rounded-full bg-primary/15"
                >
                  <div class="animate-mh-indeterminate h-full w-1/3 rounded-full bg-primary"></div>
                </div>
              }
            </div>
            @if (candidates.error()) {
              <div class="flex flex-wrap items-center gap-3 py-2">
                <p role="alert" class="text-sm text-destructive">Could not load the mentors.</p>
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
            @if (loaded(); as page) {
              @if (page.items.length > 0) {
                <ul
                  class="max-h-72 min-w-0 divide-y divide-border overflow-y-auto rounded-md border border-border [scrollbar-gutter:stable]"
                >
                  @for (c of page.items; track c.id) {
                    <li>
                      <label
                        [for]="'mentor-' + c.id"
                        class="flex min-h-11 min-w-0 cursor-pointer flex-wrap items-center gap-x-3 ps-3 pe-4 py-2 hover:bg-muted/60 has-[:focus-visible]:bg-muted/60 sm:flex-nowrap"
                      >
                        <hlm-checkbox
                          [inputId]="'mentor-' + c.id"
                          [aria-label]="'Choose ' + c.name"
                          [aria-describedby]="'load-' + c.id"
                          [checked]="chosen().has(c.id)"
                          (checkedChange)="toggle(c)"
                        />
                        <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                          <span class="font-medium [overflow-wrap:anywhere]">{{ c.name }}</span>
                          <span
                            class="block font-mono text-xs [overflow-wrap:anywhere] text-muted-foreground"
                            >{{ c.email }}</span
                          >
                        </span>
                        <span
                          [id]="'load-' + c.id"
                          class="basis-full ps-8 text-xs text-muted-foreground sm:basis-auto sm:ps-0 sm:text-end sm:whitespace-nowrap"
                          >{{ loadOf(c) }}</span
                        >
                      </label>
                    </li>
                  }
                </ul>
              } @else if (!candidates.isLoading() && !candidates.error()) {
                <p
                  role="status"
                  class="rounded-md border border-dashed border-border px-3 py-4 text-sm text-muted-foreground"
                >
                  {{ emptyText() }}
                </p>
              }
            }
          </div>

          <div class="flex min-w-0 flex-wrap items-center gap-x-4 gap-y-2">
            <span role="status" class="text-sm font-medium">{{ countText() }}</span>
            @if (pages() > 1) {
              <nav
                aria-label="Candidate pages"
                class="ms-auto flex items-center gap-2 text-sm text-muted-foreground"
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

        <div hlmDialogFooter class="items-center">
          @if (chosen().size === 0) {
            <span
              id="assign-mentors-hint"
              class="text-sm text-muted-foreground max-sm:w-full sm:me-auto"
              >Choose at least one mentor.</span
            >
          }
          <button hlmBtn variant="outline" type="button" (click)="ref.close(false)">Cancel</button>
          <button
            hlmBtn
            type="submit"
            [attr.aria-disabled]="chosen().size === 0 || saving() ? 'true' : null"
            [attr.aria-describedby]="chosen().size === 0 ? 'assign-mentors-hint' : null"
          >
            {{ submitLabel() }}
          </button>
        </div>
      </form>
    </div>
  `,
})
export class AssignCohortMentorsDialog {
  protected readonly data = injectBrnDialogContext<AssignCohortMentorsDialogData>();
  protected readonly ref = inject<BrnDialogRef<boolean>>(BrnDialogRef);
  private readonly pending = inject(PendingTasks);
  private searchTimer: ReturnType<typeof setTimeout> | undefined;
  private releaseSearch: (() => void) | undefined;

  protected readonly loadOf = loadText;
  protected readonly typed = signal('');
  private readonly term = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly chosen = signal<ReadonlyMap<string, MentorAssignmentCandidate>>(new Map());
  protected readonly saving = signal(false);

  protected readonly candidates = resource({
    params: () => ({ q: this.term(), page: this.pageIndex() }),
    loader: async ({ params }): Promise<MentorAssignmentCandidatePage | null> => {
      const page = await this.data.load(params.q, params.page);
      if (page === null) {
        this.ref.close(false);
      }
      return page;
    },
  });
  /** The last loaded page: it stays on screen while the next one loads. */
  protected readonly loaded = linkedSignal<
    MentorAssignmentCandidatePage | null | undefined,
    MentorAssignmentCandidatePage | null
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
    return q
      ? `No mentors match "${q}". Check the spelling.`
      : `There are no other active mentors on this track to assign to ${this.data.cohortName}.`;
  });

  protected readonly submitLabel = computed(() => {
    const n = this.chosen().size;
    return n === 0 ? 'Assign mentors' : `Assign ${mentorsText(n)}`;
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.searchTimer);
      this.releaseSearch?.();
    });
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

  protected toggle(candidate: MentorAssignmentCandidate): void {
    const next = new Map(this.chosen());
    if (!next.delete(candidate.id)) {
      next.set(candidate.id, candidate);
    }
    this.chosen.set(next);
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.saving() || this.chosen().size === 0) {
      return;
    }
    this.saving.set(true);
    try {
      if (await this.data.save([...this.chosen().keys()])) {
        this.ref.close(true);
      }
    } finally {
      this.saving.set(false);
    }
  }
}
