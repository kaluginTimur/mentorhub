import { DatePipe, NgTemplateOutlet } from '@angular/common';
import {
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  resource,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucidePlus, lucideTriangleAlert, lucideUserMinus } from '@ng-icons/lucide';
import type { BrnOverlayState } from '@spartan-ng/brain/overlay';
import { HlmPopoverImports } from '@spartan-ng/helm/popover';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { Api } from '../../core/api/api';
import { removeEnrollmentMentor } from '../../core/api/fn/enrollments/remove-enrollment-mentor';
import { enrollStudent } from '../../core/api/fn/enrollments/enroll-student';
import { listEnrollmentCandidates } from '../../core/api/fn/enrollments/list-enrollment-candidates';
import { listTrackEnrollments } from '../../core/api/fn/enrollments/list-track-enrollments';
import { EnrollmentMentor } from '../../core/api/models/enrollment-mentor';
import { TrackEnrollmentPage } from '../../core/api/models/track-enrollment-page';
import { TrackStatus } from '../../core/api/models/track-status';
import { PROBLEM, problemOf } from '../../core/problem';
import { DATE_FORMAT } from '../../shared/date/date-format';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/confirm-dialog/confirm-dialog';
import { Paginator } from '../../shared/paginator/paginator';
import { ScrollIntoViewOnChange } from '../../shared/scroll/scroll-into-view-on-change';
import { ToastService } from '../../shared/toast/toast.service';
import { AssignmentMark } from '../tracks/assignment-mark';
import { AssignMentorDialog, AssignMentorResult } from './assign-mentor-dialog';
import { EnrollStudentDialog, EnrollStudentDialogData } from './enroll-student-dialog';
import { Email } from '../../shared/email/email';
import { firstValueFrom } from 'rxjs';

const PAGE_SIZE = 25;
const PAGE_PARAM = 'enrollmentsPage';

/** The enrollments of a track, for a user with ENROLLMENTS_READ (ENR-7). */
@Component({
  selector: 'app-track-enrollments',
  imports: [
    ScrollIntoViewOnChange,
    Email,
    DatePipe,
    NgTemplateOutlet,
    NgIcon,
    HlmButton,
    HlmCardImports,
    AssignmentMark,
    HlmPopoverImports,
    Paginator,
  ],
  providers: [provideIcons({ lucidePlus, lucideTriangleAlert, lucideUserMinus })],
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
        @if (enrollments.error()) {
          <div class="flex flex-wrap items-center gap-3">
            <p role="alert" class="text-sm text-destructive">Could not load the enrollments.</p>
            <button hlmBtn variant="outline" size="sm" type="button" (click)="enrollments.reload()">
              Retry
            </button>
          </div>
        }
        @if (loaded(); as page) {
          @if (page.items.length > 0) {
            <div
              aria-hidden="true"
              class="hidden gap-x-4 border-b border-border pb-1.5 text-[11px] font-normal tracking-wider text-muted-foreground uppercase md:flex"
            >
              <span class="flex-1">Student</span>
              <span class="w-64">Mentors</span>
              <span class="w-32 text-end">Start</span>
            </div>
            <ul class="divide-y divide-border">
              @for (e of page.items; track e.id) {
                <li class="py-2.5">
                  <div class="flex flex-wrap items-start gap-x-4 gap-y-2">
                    <div class="flex min-w-0 flex-1 basis-0 flex-col gap-1">
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
                    <div
                      role="group"
                      aria-label="Mentors"
                      class="relative order-last flex basis-full items-center gap-2 text-sm text-muted-foreground md:order-none md:w-64 md:shrink-0 md:basis-auto md:self-center"
                    >
                      @let ms = ordered(e.mentors);
                      @if (ms.length > 0) {
                        @let first = ms[0];
                        <span class="shrink-0 md:hidden">{{
                          ms.length > 1 ? 'Mentors:' : 'Mentor:'
                        }}</span>
                        <hlm-popover
                          class="min-w-0"
                          align="start"
                          sideOffset="6"
                          [state]="popoverState(e.id, first.id)"
                          (stateChanged)="onPopover(e.id, first.id, $event)"
                        >
                          <button
                            hlmPopoverTrigger
                            type="button"
                            class="relative flex max-w-full min-w-0 items-center gap-1 rounded-sm text-start hover:underline focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none after:pointer-events-none after:absolute after:bottom-full after:left-0 after:z-20 after:mb-1.5 after:rounded-md after:bg-foreground after:px-2 after:py-1 after:text-xs after:font-normal after:whitespace-nowrap after:text-background after:opacity-0 after:transition-opacity after:content-[attr(data-tip)] data-[open=true]:after:invisible"
                            [class]="
                              isMarked(first)
                                ? 'text-muted-foreground hover:text-foreground hover:after:opacity-100 focus-visible:after:opacity-100'
                                : 'text-muted-foreground hover:text-foreground'
                            "
                            [attr.data-tip]="isMarked(first) ? markOnly(first) : null"
                            [attr.aria-label]="mentorLabel(first)"
                            [attr.data-open]="popoverState(e.id, first.id) === 'open'"
                          >
                            <span class="truncate">{{ first.name }}</span>
                            @if (isMarked(first)) {
                              <ng-icon
                                name="lucideTriangleAlert"
                                size="14"
                                class="shrink-0 !text-mh-warning-soft-foreground opacity-100"
                                aria-hidden="true"
                              />
                            }
                            <span class="sr-only">{{ markText(first) }}</span>
                          </button>
                          <hlm-popover-content *hlmPopoverPortal class="w-72 p-3">
                            <ng-container
                              *ngTemplateOutlet="mentorCard; context: { $implicit: first, e: e }"
                            />
                          </hlm-popover-content>
                        </hlm-popover>
                        @if (ms.length > 1) {
                          <hlm-popover
                            align="start"
                            sideOffset="6"
                            [state]="popoverState(e.id, '+')"
                            (stateChanged)="onPopover(e.id, '+', $event)"
                          >
                            <button
                              hlmPopoverTrigger
                              type="button"
                              class="relative flex shrink-0 items-center gap-0.5 rounded-sm px-1 hover:text-foreground hover:underline focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none after:pointer-events-none after:absolute after:bottom-full after:left-0 after:z-20 after:mb-1.5 after:rounded-md after:bg-foreground after:px-2 after:py-1 after:text-xs after:font-normal after:whitespace-nowrap after:text-background after:opacity-0 after:transition-opacity after:content-[attr(data-tip)] data-[open=true]:after:invisible"
                              [class]="
                                inactiveCount(ms) > 0
                                  ? 'hover:after:opacity-100 focus-visible:after:opacity-100'
                                  : ''
                              "
                              [attr.data-tip]="inactiveCount(ms) > 0 ? moreLabel(ms) : null"
                              [attr.data-open]="popoverState(e.id, '+') === 'open'"
                              [attr.aria-label]="moreLabel(ms)"
                            >
                              +{{ ms.length - 1 }}
                              @if (inactiveCount(ms) > 0) {
                                <ng-icon
                                  name="lucideTriangleAlert"
                                  size="14"
                                  class="!text-mh-warning-soft-foreground opacity-100"
                                  aria-hidden="true"
                                />
                              }
                            </button>
                            <hlm-popover-content *hlmPopoverPortal class="w-72 divide-y p-3">
                              @for (m of ms.slice(1); track m.id) {
                                <div class="py-2 first:pt-0 last:pb-0">
                                  <ng-container
                                    *ngTemplateOutlet="mentorCard; context: { $implicit: m, e: e }"
                                  />
                                </div>
                              }
                            </hlm-popover-content>
                          </hlm-popover>
                        }
                      } @else {
                        <span aria-hidden="true" class="text-muted-foreground/60">—</span>
                      }
                      @if (canEnroll()) {
                        <button
                          hlmBtn
                          variant="ghost"
                          size="icon-sm"
                          type="button"
                          class="relative size-6 shrink-0 rounded-full text-muted-foreground after:pointer-events-none after:absolute after:bottom-full after:left-0 after:z-20 after:mb-1.5 after:rounded-md after:bg-foreground after:px-2 after:py-1 after:text-xs after:font-normal after:whitespace-nowrap after:text-background after:opacity-0 after:transition-opacity after:content-['Assign_mentor'] hover:after:opacity-100 focus-visible:after:opacity-100"
                          aria-label="Assign mentor"
                          (click)="assignMentor(e.id, e.student.name)"
                        >
                          <ng-icon name="lucidePlus" aria-hidden="true" />
                          <span class="sr-only">Assign mentor</span>
                        </button>
                      }
                    </div>
                    <span class="shrink-0 text-sm text-muted-foreground md:w-32 md:text-end">
                      From
                      <time [attr.datetime]="e.startDate">{{
                        e.startDate | date: dateFormat
                      }}</time>
                    </span>
                  </div>
                </li>
              }
            </ul>
          } @else if (page.totalItems > 0) {
            <p class="text-xs text-muted-foreground">No students on this page.</p>
          } @else if (!enrollments.isLoading() && !enrollments.error()) {
            <p class="text-xs text-muted-foreground">No students enrolled yet</p>
          }
          @if (page.totalItems > PAGE_SIZE || (page.totalItems > 0 && pageIndex() > 0)) {
            <app-paginator
              borderless
              [totalItems]="page.totalItems"
              [pageSize]="PAGE_SIZE"
              [pageIndex]="pageIndex()"
              (pageChange)="onPage($event)"
            />
          }
        }
      </div>
    </div>
    <ng-template #mentorCard let-m let-e="e">
      <div class="flex items-center justify-between gap-3">
        <div class="flex min-w-0 flex-col gap-1">
          <span class="font-medium break-words">{{ m.name }}</span>
          <span class="font-mono text-xs break-all text-muted-foreground"
            ><app-email [value]="m.email"
          /></span>
          @if (isMarked(m)) {
            <div class="flex flex-wrap gap-1.5 pt-1">
              @if (m.disabled) {
                <app-assignment-mark label="Disabled" />
              }
              @if (!m.mentorRole) {
                <app-assignment-mark label="No mentor role" />
              }
            </div>
          }
        </div>
        @if (canEnroll()) {
          <button
            hlmBtn
            variant="outline"
            size="icon-sm"
            type="button"
            [attr.aria-label]="'Remove ' + m.name"
            class="relative shrink-0 border-destructive/40 bg-card text-destructive hover:bg-destructive/10 hover:text-mh-destructive-hover-foreground dark:border-destructive/50 dark:bg-card dark:hover:bg-destructive/20 after:pointer-events-none after:absolute after:right-0 after:bottom-full after:z-20 after:mb-1.5 after:rounded-md after:bg-foreground after:px-2 after:py-1 after:text-xs after:font-normal after:text-background after:opacity-0 after:transition-opacity after:content-['Remove'] hover:after:opacity-100 focus-visible:after:opacity-100"
            (click)="closePopovers(); removeMentor(e.id, e.student.name, m)"
          >
            <ng-icon name="lucideUserMinus" aria-hidden="true" />
            <span class="sr-only">Remove</span>
          </button>
        }
      </div>
    </ng-template>
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

  /** Shows the enrollments as they are now. */
  reload(): void {
    this.enrollments.reload();
  }

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

  /** The mentor whose popover is open, as `enrollmentId/mentorId`. */
  private readonly openPopover = signal<string | null>(null);

  protected popoverState(enrollmentId: string, mentorId: string): BrnOverlayState {
    return this.openPopover() === `${enrollmentId}/${mentorId}` ? 'open' : 'closed';
  }

  protected onPopover(enrollmentId: string, mentorId: string, state: BrnOverlayState): void {
    const key = `${enrollmentId}/${mentorId}`;
    if (state === 'open') {
      this.openPopover.set(key);
    } else if (this.openPopover() === key) {
      this.openPopover.set(null);
    }
  }

  protected closePopovers(): void {
    this.openPopover.set(null);
  }

  /** Active mentors first, so the name shown in the row is one that can act. */
  protected ordered(mentors: EnrollmentMentor[]): EnrollmentMentor[] {
    return [...mentors].sort((a, b) => Number(this.isMarked(a)) - Number(this.isMarked(b)));
  }

  /** Marked mentors behind "+N", counted without repeating the mark words. */
  protected inactiveCount(ms: EnrollmentMentor[]): number {
    return ms.slice(1).filter((m) => this.isMarked(m)).length;
  }

  protected moreLabel(ms: EnrollmentMentor[]): string {
    const inactive = this.inactiveCount(ms);
    const more = ms.length - 1;
    return `${more} more ${more === 1 ? 'mentor' : 'mentors'}${inactive > 0 ? `, ${inactive} inactive` : ''}`;
  }

  protected markOnly(m: EnrollmentMentor): string {
    return this.markText(m).slice(2);
  }

  protected isMarked(m: EnrollmentMentor): boolean {
    return m.disabled || !m.mentorRole;
  }

  /** The marks as words, so a mark is never carried by the dimmed avatar alone. */
  protected markText(m: EnrollmentMentor): string {
    const marks = [m.disabled ? 'Disabled' : '', m.mentorRole ? '' : 'No mentor role'].filter(
      Boolean,
    );
    return marks.length > 0 ? `, ${marks.join(', ')}` : '';
  }

  protected mentorLabel(m: EnrollmentMentor): string {
    return `${m.name}${this.markText(m)}`;
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

  protected async assignMentor(enrollmentId: string, studentName: string): Promise<void> {
    const ref = this.dialog.open<AssignMentorResult | undefined>(AssignMentorDialog, {
      contentClass: 'sm:max-w-[520px]',
      context: { enrollmentId, studentName },
    });
    const result = await firstValueFrom(ref.closed$);
    if (result === true) {
      this.say('Mentor assigned', { duration: 5000, politeness: 'polite' });
      this.enrollments.reload();
      this.enrolled.emit();
    } else if (result === 'refused') {
      this.say(
        'That user can no longer be assigned: they must be active, hold the mentor role and be a mentor of this track. The enrollments are shown as they are now.',
        { politeness: 'assertive' },
      );
      this.enrollments.reload();
      this.refused.emit();
    }
  }

  protected async removeMentor(
    enrollmentId: string,
    studentName: string,
    mentor: EnrollmentMentor,
  ): Promise<void> {
    const ref = this.dialog.open<boolean, ConfirmDialogData>(ConfirmDialog, {
      contentClass: 'sm:max-w-[420px]',
      context: {
        title: `Remove ${mentor.name} as a mentor of ${studentName}?`,
        text: 'They stop being a mentor of this student. They can be assigned again later.',
        confirmLabel: 'Remove',
        destructive: true,
      },
    });
    if (!(await firstValueFrom(ref.closed$))) {
      return;
    }
    try {
      await this.api.invoke(removeEnrollmentMentor, { id: enrollmentId, userId: mentor.id });
    } catch (error) {
      if (problemOf(error)?.status === 404) {
        this.say('This enrollment no longer exists. It is shown as it is now.', {
          politeness: 'assertive',
        });
        this.enrollments.reload();
        this.refused.emit();
      } else if (problemOf(error)?.status !== 403) {
        this.say('Could not remove the mentor. Try again.');
      }
      return;
    }
    this.say(`${mentor.name} removed`, { duration: 5000, politeness: 'polite' });
    this.enrollments.reload();
    this.enrolled.emit();
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
