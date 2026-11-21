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
import { HlmLabel } from '@spartan-ng/helm/label';
import { Api } from '../../core/api/api';
import { assignEnrollmentMentor } from '../../core/api/fn/enrollments/assign-enrollment-mentor';
import { listMentorCandidates } from '../../core/api/fn/enrollments/list-mentor-candidates';
import { MentorCandidate } from '../../core/api/models/mentor-candidate';
import { MentorCandidatePage } from '../../core/api/models/mentor-candidate-page';
import { PROBLEM, problemOf } from '../../core/problem';
import { Email } from '../../shared/email/email';

const SEARCH_LIMIT = 100;
const SEARCH_DELAY = 300;
const PAGE_SIZE = 25;

export interface AssignMentorDialogData {
  enrollmentId: string;
  studentName: string;
}

/** What the dialog closes with: assigned, or refused by the server (the opener explains it); dismissed gives undefined. */
export type AssignMentorResult = true | 'refused';

/** The dialog that assigns a mentor to an enrollment (ENR-7). */
@Component({
  selector: 'app-assign-mentor-dialog',
  imports: [
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogDescription,
    HlmDialogFooter,
    HlmButton,
    HlmAutocompleteImports,
    Email,
    HlmLabel,
  ],
  template: `
    <div class="flex flex-col gap-5">
      <div hlmDialogHeader>
        <h2
          hlmDialogTitle
          class="pe-10 font-display text-lg font-semibold tracking-tight break-words"
        >
          Assign a mentor to {{ data.studentName }}
        </h2>
        <p hlmDialogDescription>
          Only active users with the mentor role who are mentors of this track and not mentors of
          this student yet are listed.
        </p>
      </div>

      <form novalidate (submit)="onSubmit($event)" class="space-y-4">
        <div class="flex flex-col gap-2.5">
          <label hlmLabel for="assign-mentor-search">Mentor</label>
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
              inputId="assign-mentor-search"
              placeholder="Search by name or email"
              showClear
              (click)="panelState.set('open')"
              [aria-invalid]="missing() ? true : undefined"
              [attr.aria-describedby]="missing() ? 'assign-mentor-error' : 'assign-mentor-chosen'"
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
                  <div hlmAutocompleteEmpty>No mentor matches the search.</div>
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
                aria-label="Loading mentors"
                class="h-1 w-full overflow-hidden rounded-full bg-primary/15"
              >
                <div class="animate-mh-indeterminate h-full w-1/3 rounded-full bg-primary"></div>
              </div>
            }
          </div>

          @if (failed()) {
            <div class="flex flex-wrap items-center gap-3">
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

          <!-- One line is reserved for the chosen mentor or an error, so the dialog keeps its size. -->
          <div class="min-h-4">
            <p
              id="assign-mentor-chosen"
              class="text-xs text-muted-foreground"
              aria-live="polite"
              [class.hidden]="missing() || saveFailed()"
            >
              @if (selected(); as s) {
                Chosen:
                <span class="font-mono text-foreground"><app-email [value]="s.email" /></span>
              }
            </p>
            @if (missing()) {
              <p id="assign-mentor-error" class="text-xs text-destructive" role="alert">
                Choose a mentor
              </p>
            } @else if (saveFailed()) {
              <p class="text-xs text-destructive" role="alert">
                Could not assign the mentor. Try again.
              </p>
            }
          </div>
        </div>

        <div hlmDialogFooter>
          <button hlmBtn variant="outline" type="button" (click)="ref.close()">Cancel</button>
          <button hlmBtn type="submit" [disabled]="saving()">Assign</button>
        </div>
      </form>
    </div>
  `,
})
export class AssignMentorDialog {
  protected readonly data = injectBrnDialogContext<AssignMentorDialogData>();
  protected readonly ref = inject<BrnDialogRef<AssignMentorResult>>(BrnDialogRef);
  private readonly api = inject(Api);
  private readonly pending = inject(PendingTasks);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly panelState = signal<BrnOverlayState | null>(null);
  protected readonly candidateName = (c: MentorCandidate) => c.name;
  protected readonly sameCandidate = (a: MentorCandidate, b: MentorCandidate | null | undefined) =>
    a.id === b?.id;
  /** The search applied to the list: it follows the text 300 ms after typing stops. */
  protected readonly term = signal('');
  private searchTimer: ReturnType<typeof setTimeout> | undefined;
  private releaseSearch: (() => void) | undefined;

  protected readonly selected = signal<MentorCandidate | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveFailed = signal(false);
  private readonly submitted = signal(false);
  protected readonly missing = computed(() => this.submitted() && this.selected() === null);

  protected readonly candidates = resource({
    params: () => this.term(),
    loader: async ({ params }): Promise<MentorCandidatePage> => {
      try {
        return await this.api.invoke(listMentorCandidates, {
          id: this.data.enrollmentId,
          q: params || undefined,
          size: PAGE_SIZE,
        });
      } catch (error) {
        if (problemOf(error)?.status === 404) {
          // The enrollment is gone: the opener shows the list as it is now.
          this.ref.close('refused');
        }
        throw error;
      }
    },
  });
  protected readonly failed = computed(() => !!this.candidates.error());
  /** The last loaded page: it stays on screen while the next one loads. */
  protected readonly loaded = linkedSignal<
    MentorCandidatePage | undefined,
    MentorCandidatePage | null
  >({
    source: () => (this.candidates.hasValue() ? this.candidates.value() : undefined),
    computation: (page, previous) => page ?? previous?.value ?? null,
  });

  constructor() {
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
      this.term.set(value.trim().slice(0, SEARCH_LIMIT));
      this.releaseSearch?.();
      this.releaseSearch = undefined;
    }, SEARCH_DELAY);
  }

  protected onChoose(candidate: MentorCandidate | null | undefined): void {
    this.selected.set(candidate ?? null);
    this.saveFailed.set(false);
    if (!candidate) {
      this.panelState.set('open');
      // The clear button leaves the page when it is used: the focus returns to the field.
      this.host.nativeElement.querySelector<HTMLInputElement>('#assign-mentor-search')?.focus();
    }
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.saving()) {
      return;
    }
    this.submitted.set(true);
    const mentor = this.selected();
    if (mentor === null) {
      return;
    }
    this.saving.set(true);
    this.saveFailed.set(false);
    try {
      await this.api.invoke(assignEnrollmentMentor, {
        id: this.data.enrollmentId,
        userId: mentor.id,
      });
      this.ref.close(true);
    } catch (error) {
      const problem = problemOf(error);
      if (problem?.type === PROBLEM.mentorNotEligible || problem?.status === 404) {
        this.ref.close('refused');
      } else if (problem?.status !== 403) {
        this.saveFailed.set(true);
      }
    } finally {
      this.saving.set(false);
    }
  }
}
