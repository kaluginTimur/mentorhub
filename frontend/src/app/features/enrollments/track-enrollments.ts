import { DatePipe } from '@angular/common';
import {
  Component,
  computed,
  effect,
  inject,
  input,
  linkedSignal,
  output,
  resource,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucidePlus } from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { Api } from '../../core/api/api';
import { enrollStudent } from '../../core/api/fn/enrollments/enroll-student';
import { listEnrollmentCandidates } from '../../core/api/fn/enrollments/list-enrollment-candidates';
import { listTrackEnrollments } from '../../core/api/fn/enrollments/list-track-enrollments';
import { TrackEnrollmentPage } from '../../core/api/models/track-enrollment-page';
import { TrackStatus } from '../../core/api/models/track-status';
import { PROBLEM, problemOf } from '../../core/problem';
import { DATE_FORMAT } from '../../shared/date/date-format';
import { Paginator } from '../../shared/paginator/paginator';
import { ScrollIntoViewOnChange } from '../../shared/scroll/scroll-into-view-on-change';
import { ToastService } from '../../shared/toast/toast.service';
import { AssignmentMark } from '../tracks/assignment-mark';
import { EnrollStudentDialog, EnrollStudentDialogData } from './enroll-student-dialog';
import { Email } from '../../shared/email/email';

const PAGE_SIZE = 25;
const PAGE_PARAM = 'enrollmentsPage';

/** The enrollments of a track, for a user with ENROLLMENTS_READ (ENR-7). */
@Component({
  selector: 'app-track-enrollments',
  imports: [
    ScrollIntoViewOnChange,
    Email,
    DatePipe,
    NgIcon,
    HlmButton,
    HlmCardImports,
    AssignmentMark,
    Paginator,
  ],
  providers: [provideIcons({ lucidePlus })],
  template: `
    <div hlmCard class="scroll-mt-20" [appScrollIntoViewOnChange]="pageIndex()">
      <div hlmCardHeader class="items-center">
        <h2 hlmCardTitle>Enrollments</h2>
        @if (canEnroll() && trackStatus() === 'ACTIVE') {
          <button
            hlmBtn
            hlmCardAction
            variant="outline"
            size="sm"
            type="button"
            class="self-center"
            (click)="enroll()"
          >
            <ng-icon name="lucidePlus" aria-hidden="true" />
            Enroll student
          </button>
        }
      </div>
      <div hlmCardContent class="relative space-y-3" [attr.aria-busy]="enrollments.isLoading()">
        <div class="absolute inset-x-(--card-spacing) -top-2 h-1">
          @if (enrollments.isLoading()) {
            <div
              role="progressbar"
              aria-label="Loading enrollments"
              class="h-1 w-full overflow-hidden rounded-full bg-primary/15"
            >
              <div class="animate-mh-indeterminate h-full w-1/3 rounded-full bg-primary"></div>
            </div>
          }
        </div>
        @if (enrollments.error() && !loaded()) {
          <div class="flex flex-wrap items-center gap-3">
            <p role="alert" class="text-sm text-destructive">Could not load the enrollments.</p>
            <button hlmBtn variant="outline" size="sm" type="button" (click)="enrollments.reload()">
              Retry
            </button>
          </div>
        } @else if (loaded(); as page) {
          @if (page.items.length > 0) {
            <ul class="divide-y divide-border">
              @for (e of page.items; track e.id) {
                <li class="flex items-start justify-between gap-x-4 gap-y-2 py-2.5">
                  <div class="flex min-w-0 flex-1 flex-col gap-1">
                    <div class="flex flex-wrap items-center gap-2">
                      <span class="font-medium break-words">{{ e.student.name }}</span>
                      @if (e.student.disabled) {
                        <app-assignment-mark label="Disabled" />
                      }
                      @if (!e.student.studentRole) {
                        <app-assignment-mark label="No student role" />
                      }
                    </div>
                    <span class="block font-mono text-xs text-muted-foreground"
                      ><app-email [value]="e.student.email"
                    /></span>
                  </div>
                  <span class="shrink-0 text-sm text-muted-foreground">
                    From
                    <time [attr.datetime]="e.startDate">{{ e.startDate | date: dateFormat }}</time>
                  </span>
                </li>
              }
            </ul>
            @if (page.totalItems > PAGE_SIZE) {
              <app-paginator
                [totalItems]="page.totalItems"
                [pageSize]="PAGE_SIZE"
                [pageIndex]="pageIndex()"
                (pageChange)="onPage($event)"
              />
            }
          } @else if (!enrollments.isLoading()) {
            <p class="text-xs text-muted-foreground">No students enrolled yet</p>
          }
        }
      </div>
    </div>
  `,
})
export class TrackEnrollments {
  protected readonly dateFormat = DATE_FORMAT;
  readonly trackId = input.required<string>();
  readonly canEnroll = input.required<boolean>();
  readonly trackStatus = input.required<TrackStatus>();
  /** A student was enrolled: the track's history has a new entry. */
  readonly enrolled = output<void>();
  /** The server refused an enrollment or the candidates and the message was shown: reload the track. */
  readonly refused = output<void>();

  private readonly api = inject(Api);
  private readonly dialog = inject(HlmDialogService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly PAGE_SIZE = PAGE_SIZE;

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly pageIndex = computed(() => {
    const value = Number(this.params().get(PAGE_PARAM));
    return Number.isInteger(value) && value > 0 ? value : 0;
  });

  protected readonly enrollments = resource({
    params: () => ({ id: this.trackId(), page: this.pageIndex() }),
    loader: ({ params }) =>
      this.api.invoke(listTrackEnrollments, { id: params.id, page: params.page, size: PAGE_SIZE }),
  });
  /** The last loaded page: it stays on screen while the next one loads. */
  protected readonly loaded = linkedSignal<
    TrackEnrollmentPage | undefined,
    TrackEnrollmentPage | undefined
  >({
    source: () => (this.enrollments.hasValue() ? this.enrollments.value() : undefined),
    computation: (page, previous) => page ?? previous?.value,
  });

  constructor() {
    effect(() => {
      // A page past the end (after the track changed) falls back to the first one.
      const page = this.loaded();
      if (page && page.items.length === 0 && page.totalItems > 0 && this.pageIndex() > 0) {
        this.onPage(0);
      }
    });
  }

  protected onPage(index: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { [PAGE_PARAM]: index || null },
      queryParamsHandling: 'merge',
    });
  }

  protected enroll(): void {
    const id = this.trackId();
    const data: EnrollStudentDialogData = {
      load: async (q) => {
        try {
          return await this.api.invoke(listEnrollmentCandidates, {
            id,
            q: q || undefined,
            size: PAGE_SIZE,
          });
        } catch (error) {
          if (this.explainRefusal(error)) {
            return null;
          }
          throw error;
        }
      },
      enroll: async (studentId, startDate) => {
        try {
          await this.api.invoke(enrollStudent, { id, body: { studentId, startDate } });
        } catch (error) {
          if (this.explainRefusal(error)) {
            return true;
          }
          const problem = problemOf(error);
          const dateError = problem?.errors.find((e) => e.field === 'startDate');
          if (problem?.type === PROBLEM.validationFailed && dateError) {
            return dateError.message;
          }
          if (problem?.status !== 403) {
            this.say('Could not enroll the student. Try again.');
          }
          return false;
        }
        this.say('Student enrolled', { duration: 5000, politeness: 'polite' });
        this.enrollments.reload();
        this.enrolled.emit();
        return true;
      },
    };
    this.dialog.open(EnrollStudentDialog, { contentClass: 'sm:max-w-[520px]', context: data });
  }

  /** A new snackbar replaces the previous one, so a refusal does not linger beside the next result. */
  private say(
    message: string,
    options?: { duration?: number; politeness?: 'polite' | 'assertive' },
  ) {
    this.toast.dismissAll();
    this.toast.show(message, options);
  }

  /** Explains a 409 and shows the enrollments as they are now; false for any other error. */
  private explainRefusal(error: unknown): boolean {
    const type = problemOf(error)?.type;
    if (type === PROBLEM.trackArchived) {
      this.say("This track is archived, so students can't be enrolled. It is shown as it is now.", {
        politeness: 'assertive',
      });
    } else if (type === PROBLEM.studentNotEligible) {
      this.say(
        'That student can no longer be enrolled: they must be active, hold the student role and have no active enrollment.',
        { politeness: 'assertive' },
      );
    } else if (type === PROBLEM.studentAlreadyEnrolled) {
      this.say('That student is already enrolled on a track.', { politeness: 'assertive' });
    } else {
      return false;
    }
    this.enrollments.reload();
    this.refused.emit();
    return true;
  }
}
