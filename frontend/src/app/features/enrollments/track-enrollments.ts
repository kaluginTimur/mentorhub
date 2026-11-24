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
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideEllipsisVertical,
  lucideLoaderCircle,
  lucidePlus,
  lucideTriangleAlert,
  lucideUserMinus,
  lucideUserPlus,
  lucideUserRoundPlus,
  lucideUsers,
} from '@ng-icons/lucide';
import type { BrnOverlayState } from '@spartan-ng/brain/overlay';
import { HlmPopoverImports } from '@spartan-ng/helm/popover';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmDropdownMenuImports } from '@spartan-ng/helm/dropdown-menu';
import { Api } from '../../core/api/api';
import { createCohort } from '../../core/api/fn/cohorts/create-cohort';
import { addCohortEnrollments } from '../../core/api/fn/cohorts/add-cohort-enrollments';
import { deleteCohort } from '../../core/api/fn/cohorts/delete-cohort';
import { listCohortCandidates } from '../../core/api/fn/cohorts/list-cohort-candidates';
import { listNewCohortCandidates } from '../../core/api/fn/cohorts/list-new-cohort-candidates';
import { listTrackCohorts } from '../../core/api/fn/cohorts/list-track-cohorts';
import { removeCohortEnrollment } from '../../core/api/fn/cohorts/remove-cohort-enrollment';
import { removeEnrollmentMentor } from '../../core/api/fn/enrollments/remove-enrollment-mentor';
import { enrollStudent } from '../../core/api/fn/enrollments/enroll-student';
import { listEnrollmentCandidates } from '../../core/api/fn/enrollments/list-enrollment-candidates';
import { listTrackEnrollments } from '../../core/api/fn/enrollments/list-track-enrollments';
import { Cohort } from '../../core/api/models/cohort';
import { EnrollmentMentor } from '../../core/api/models/enrollment-mentor';
import { TrackEnrollment } from '../../core/api/models/track-enrollment';
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
import { CandidateLoader, PICKER_PAGE_SIZE } from './cohort-candidate-picker';
import {
  CohortFormErrors,
  CohortFormValue,
  CohortStudentsDialog,
  CohortStudentsDialogData,
} from './cohort-dialogs';
import { EnrollStudentDialog, EnrollStudentDialogData } from './enroll-student-dialog';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { Email } from '../../shared/email/email';
import { firstValueFrom } from 'rxjs';

interface ActionMenuItem {
  key: string;
  label: string;
  testId: string;
  destructive?: boolean;
}

const PAGE_SIZE = 25;
const PAGE_PARAM = 'enrollmentsPage';
const COHORT_PARAM = 'cohort';
const NO_COHORTS: Cohort[] = [];

const HEADER_ITEMS: ActionMenuItem[] = [
  { key: 'create', label: 'Create cohort', testId: 'track-actions-create-cohort' },
  { key: 'enroll', label: 'Enroll student', testId: 'track-actions-enroll' },
];
const COHORT_ITEMS: ActionMenuItem[] = [
  { key: 'add', label: 'Add students', testId: 'cohort-menu-add-students' },
  { key: 'delete', label: 'Delete', testId: 'cohort-menu-delete', destructive: true },
];
const ASSIGN_ITEM: ActionMenuItem = {
  key: 'assign',
  label: 'Assign mentor',
  testId: 'enrollment-menu-assign-mentor',
};

const studentsText = (n: number) => `${n} ${n === 1 ? 'student' : 'students'}`;

/** The enrollments of a track and its cohorts, for a user with ENROLLMENTS_READ (ENR-7, COH-8). */
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
    HlmDropdownMenuImports,
    RouterLink,
  ],
  providers: [
    provideIcons({
      lucideEllipsisVertical,
      lucideLoaderCircle,
      lucidePlus,
      lucideTriangleAlert,
      lucideUserMinus,
      lucideUserPlus,
      lucideUserRoundPlus,
      lucideUsers,
    }),
  ],
  template: `
    <div
      hlmCard
      class="scroll-mt-20 overflow-visible"
      data-testid="enrollments-card"
      [appScrollIntoViewOnChange]="pageIndex()"
    >
      <div hlmCardHeader class="items-center">
        <h2 hlmCardTitle class="font-bold">Enrollments</h2>
        @if (canChange()) {
          <div hlmCardAction class="flex items-center gap-2 self-center">
            <div class="hidden items-center gap-2 md:flex">
              <button
                hlmBtn
                variant="outline"
                size="sm"
                type="button"
                data-testid="create-cohort"
                (click)="createCohortDialog()"
              >
                <ng-icon name="lucideUsers" aria-hidden="true" />
                Create cohort
              </button>
              <button
                hlmBtn
                variant="outline"
                size="sm"
                type="button"
                data-testid="enroll-student"
                (click)="enroll()"
              >
                <ng-icon name="lucidePlus" aria-hidden="true" />
                Enroll student
              </button>
            </div>
            <button
              type="button"
              class="md:hidden inline-flex size-11 items-center justify-center rounded-md text-muted-foreground transition-colors outline-none hover:bg-muted hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 aria-expanded:bg-muted aria-expanded:text-foreground"
              [hlmDropdownMenuTrigger]="headerMenu"
              align="end"
              aria-label="Track actions"
              data-testid="track-actions-menu"
            >
              <ng-icon name="lucideEllipsisVertical" size="18" aria-hidden="true" />
            </button>
            <ng-template #headerMenu>
              <hlm-dropdown-menu class="w-48">
                @for (item of headerItems; track item.key) {
                  <button
                    type="button"
                    hlmDropdownMenuItem
                    class="min-h-11 md:min-h-8"
                    [variant]="item.destructive ? 'destructive' : 'default'"
                    [attr.data-testid]="item.testId"
                    (triggered)="onHeaderAction(item.key)"
                  >
                    {{ item.label }}
                  </button>
                }
              </hlm-dropdown-menu>
            </ng-template>
          </div>
        }
      </div>
      <div hlmCardContent class="space-y-3" [attr.aria-busy]="enrollments.isLoading()">
        @if (enrollments.error()) {
          <div class="flex flex-wrap items-center gap-3">
            <p role="alert" class="text-sm text-destructive">Could not load the enrollments.</p>
            <button hlmBtn variant="outline" size="sm" type="button" (click)="enrollments.reload()">
              Retry
            </button>
          </div>
        }
        @if (cohorts.error()) {
          <div class="flex flex-wrap items-center gap-3">
            <p class="text-sm text-destructive">Could not load the cohorts.</p>
            <button hlmBtn variant="outline" size="sm" type="button" (click)="cohorts.reload()">
              Try again
            </button>
          </div>
        }
        <div [class]="hasCohorts() ? 'md:grid md:grid-cols-[17.5rem_minmax(0,1fr)] md:gap-6' : ''">
          @if (hasCohorts()) {
            <nav
              aria-label="Cohorts"
              data-testid="cohort-list"
              class="hidden min-w-0 border-e border-border pe-4 md:block"
            >
              <h3 class="mb-2 text-sm font-bold">Cohorts</h3>
              <ul class="flex flex-col gap-1">
                <li [class]="rowFrame(selectedCohort() === null)">
                  <button
                    type="button"
                    data-testid="cohort-all"
                    class="flex min-w-0 flex-1 flex-col rounded-md px-2.5 py-2 text-start outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                    [attr.aria-current]="selectedCohort() === null ? 'true' : null"
                    (click)="choose(null)"
                  >
                    <span class="font-semibold">
                      @if (selectedCohort() === null) {
                        <span aria-hidden="true">✓ </span>
                      }
                      All enrollments
                    </span>
                    <span class="text-xs" [class]="subtle(selectedCohort() === null)">{{
                      studentsText(totalEnrollments())
                    }}</span>
                  </button>
                </li>
                @for (c of cohortItems(); track c.id) {
                  @let on = selectedCohort()?.id === c.id;
                  <li [class]="rowFrame(on)">
                    <button
                      type="button"
                      data-testid="cohort-item"
                      class="flex min-w-0 flex-1 flex-col rounded-md px-2.5 py-2 text-start outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                      [attr.data-cohort-id]="c.id"
                      [attr.aria-current]="on ? 'true' : null"
                      (click)="choose(c.id)"
                    >
                      <span class="font-semibold break-words">
                        @if (on) {
                          <span aria-hidden="true">✓ </span>
                        }
                        {{ c.name }}
                      </span>
                      <span class="text-xs" [class]="subtle(on)"
                        >Starts {{ c.startDate | date: dateFormat }} ·
                        {{ studentsText(c.enrollmentCount) }}</span
                      >
                    </button>
                    @if (on && canChange()) {
                      <ng-container *ngTemplateOutlet="cohortActions; context: { $implicit: c }" />
                    }
                  </li>
                }
              </ul>
            </nav>
          }
          <div class="relative min-w-0 space-y-3">
            @if (enrollments.isLoading()) {
              <div
                role="status"
                class="pointer-events-none sticky top-20 z-10 flex h-12 justify-center"
              >
                <div
                  class="flex size-12 items-center justify-center rounded-full bg-card/90 shadow-md ring-1 ring-border"
                >
                  <ng-icon
                    name="lucideLoaderCircle"
                    size="28"
                    class="animate-spin text-primary motion-reduce:animate-none"
                    aria-hidden="true"
                  />
                  <span class="sr-only">Loading enrollments</span>
                </div>
              </div>
            }
            @if (hasCohorts()) {
              <div class="space-y-2 md:hidden">
                <div class="flex items-end gap-1.5">
                  <div class="flex min-w-0 flex-1 flex-col gap-2">
                    <label for="cohort-select" class="text-sm font-medium">Cohort</label>
                    <select
                      id="cohort-select"
                      data-testid="cohort-select"
                      class="h-11 w-full min-w-0 rounded-md border border-input bg-transparent px-2.5 text-base shadow-xs outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
                      (change)="choose($any($event.target).value || null)"
                    >
                      <option value="" [selected]="selectedCohort() === null">
                        All enrollments ({{ totalEnrollments() }})
                      </option>
                      @for (c of cohortItems(); track c.id) {
                        <option [value]="c.id" [selected]="selectedCohort()?.id === c.id">
                          {{ c.name }} - {{ studentsText(c.enrollmentCount) }}
                        </option>
                      }
                    </select>
                  </div>
                  @if (selectedCohort(); as c) {
                    @if (canChange()) {
                      <ng-container *ngTemplateOutlet="cohortActions; context: { $implicit: c }" />
                    }
                  }
                </div>
                @if (selectedCohort(); as c) {
                  <p data-testid="cohort-info" class="text-sm text-muted-foreground">
                    Starts {{ c.startDate | date: dateFormat }} ·
                    {{ studentsText(c.enrollmentCount) }}
                  </p>
                }
              </div>
            }
            @if (loaded(); as page) {
              @if (page.items.length > 0) {
                <table class="w-full text-sm md:table-fixed max-md:block">
                  <colgroup class="max-md:hidden">
                    <col class="w-[30%]" />
                    <col class="w-28" />
                    @if (hasCohorts()) {
                      <col class="w-[18%]" />
                    }
                    <col />
                    @if (canChange()) {
                      <col class="w-14" />
                    }
                  </colgroup>
                  <thead class="max-md:sr-only">
                    <tr
                      class="border-b border-border text-[11px] font-normal tracking-wider text-muted-foreground uppercase"
                    >
                      <th scope="col" class="pb-1.5 text-start font-normal">Student</th>
                      <th scope="col" class="pb-1.5 text-start font-normal">Start date</th>
                      @if (hasCohorts()) {
                        <th scope="col" class="pb-1.5 text-start font-normal">Cohort</th>
                      }
                      <th scope="col" class="pb-1.5 text-start font-normal">Mentors</th>
                      @if (canChange()) {
                        <th scope="col" class="pb-1.5 text-end font-normal">
                          <span class="sr-only">Actions</span>
                        </th>
                      }
                    </tr>
                  </thead>
                  <tbody class="max-md:block">
                    @for (e of page.items; track e.id) {
                      <tr
                        class="border-b border-border last:border-b-0 max-md:relative max-md:flex max-md:flex-col max-md:gap-1.5 max-md:py-3"
                      >
                        <td class="min-w-0 py-2.5 pe-3 align-middle max-md:py-0">
                          <div class="flex min-w-0 flex-col gap-1">
                            <div class="flex flex-wrap items-center gap-2">
                              @if (canOpenUsers()) {
                                <a
                                  data-testid="student-link"
                                  class="font-medium break-words text-mh-link hover:text-mh-link-hover hover:underline focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                                  [routerLink]="['/users', e.student.id]"
                                  >{{ e.student.name }}</a
                                >
                              } @else {
                                <span class="font-medium break-words">{{ e.student.name }}</span>
                              }
                              @if (e.student.disabled) {
                                <app-assignment-mark label="Disabled" />
                              }
                              @if (!e.student.studentRole) {
                                <app-assignment-mark label="No student role" />
                              }
                            </div>
                            <span
                              class="block truncate font-mono text-xs text-muted-foreground"
                              [attr.title]="e.student.email"
                              >{{ e.student.email }}</span
                            >
                          </div>
                        </td>
                        <td class="py-2.5 pe-3 align-middle text-muted-foreground max-md:py-0">
                          <span class="md:hidden">Started </span>
                          <time [attr.datetime]="e.startDate">{{
                            e.startDate | date: dateFormat
                          }}</time>
                        </td>
                        @if (hasCohorts()) {
                          <td class="py-2.5 pe-3 align-middle max-md:py-0">
                            <span class="md:hidden">Cohort: </span>
                            <span
                              data-testid="enrollment-cohort"
                              class="inline-flex max-w-full items-center rounded-full border px-2 py-0.5 align-middle text-xs font-medium"
                              [class]="
                                e.cohort
                                  ? 'border-transparent bg-secondary text-secondary-foreground'
                                  : 'border-border text-muted-foreground'
                              "
                              ><span class="truncate">{{
                                e.cohort?.name ?? 'No cohort'
                              }}</span></span
                            >
                          </td>
                        }
                        <td class="py-2.5 pe-3 align-middle max-md:py-0">
                          <div
                            role="group"
                            aria-label="Mentors"
                            class="relative flex min-w-0 items-center gap-2 text-sm text-muted-foreground"
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
                                    *ngTemplateOutlet="
                                      mentorCard;
                                      context: { $implicit: first, e: e }
                                    "
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
                                          *ngTemplateOutlet="
                                            mentorCard;
                                            context: { $implicit: m, e: e }
                                          "
                                        />
                                      </div>
                                    }
                                  </hlm-popover-content>
                                </hlm-popover>
                              }
                            } @else {
                              <span aria-hidden="true" class="text-muted-foreground/60">—</span>
                              <span class="sr-only">No mentor</span>
                            }
                          </div>
                        </td>
                        @if (canChange()) {
                          <td
                            class="py-1 text-end align-middle max-md:absolute max-md:inset-e-0 max-md:bottom-1.5 max-md:py-0"
                          >
                            @let items = rowItems().get(e.id) ?? [assignItem];
                            @if (items.length === 1) {
                              <button
                                type="button"
                                data-testid="enrollment-assign-mentor"
                                class="group/tip relative inline-flex size-11 items-center justify-center rounded-md text-muted-foreground transition-colors outline-none hover:bg-muted hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 md:size-8 after:pointer-events-none after:absolute after:end-0 after:bottom-full after:z-20 after:mb-1.5 after:rounded-md after:bg-foreground after:px-2 after:py-1 after:text-xs after:font-normal after:whitespace-nowrap after:text-background after:opacity-0 after:transition-opacity after:content-['Assign_mentor'] hover:after:opacity-100 focus-visible:after:opacity-100"
                                [attr.aria-label]="'Assign mentor for ' + e.student.name"
                                (click)="onRowAction(e, 'assign')"
                              >
                                <ng-icon name="lucideUserRoundPlus" size="18" aria-hidden="true" />
                                <span class="sr-only">Assign mentor</span>
                              </button>
                            } @else {
                              <button
                                type="button"
                                class="inline-flex size-11 items-center justify-center rounded-md text-muted-foreground transition-colors outline-none hover:bg-muted hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 aria-expanded:bg-muted aria-expanded:text-foreground md:size-8"
                                [hlmDropdownMenuTrigger]="rowMenu"
                                align="end"
                                [attr.aria-label]="'Actions for ' + e.student.name"
                                data-testid="enrollment-actions-menu"
                              >
                                <ng-icon
                                  name="lucideEllipsisVertical"
                                  size="18"
                                  aria-hidden="true"
                                />
                              </button>
                              <ng-template #rowMenu>
                                <hlm-dropdown-menu class="w-48">
                                  @for (item of items; track item.key) {
                                    <button
                                      type="button"
                                      hlmDropdownMenuItem
                                      class="min-h-11 md:min-h-8"
                                      [variant]="item.destructive ? 'destructive' : 'default'"
                                      [attr.data-testid]="item.testId"
                                      (triggered)="onRowAction(e, item.key)"
                                    >
                                      {{ item.label }}
                                    </button>
                                  }
                                </hlm-dropdown-menu>
                              </ng-template>
                            }
                          </td>
                        }
                      </tr>
                    }
                  </tbody>
                </table>
              } @else if (page.totalItems > 0) {
                <p class="text-xs text-muted-foreground">No students on this page.</p>
              } @else if (!enrollments.isLoading() && !enrollments.error()) {
                @if (selectedCohort(); as c) {
                  <div data-testid="cohort-empty" class="space-y-1.5 px-2 py-6.5 text-center">
                    <p class="text-sm text-muted-foreground">No students in {{ c.name }} yet.</p>
                    @if (canChange()) {
                      <p class="text-sm text-muted-foreground md:hidden">
                        Use the menu next to the cohort to add students.
                      </p>
                    }
                  </div>
                } @else {
                  <p
                    data-testid="enrollments-empty"
                    class="px-2 py-6.5 text-center text-sm text-muted-foreground"
                  >
                    No students enrolled yet
                  </p>
                }
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
      </div>
    </div>
    <ng-template #cohortActions let-c>
      @if (c.enrollmentCount > 0) {
        <button
          type="button"
          data-testid="cohort-add-students"
          class="group/tip relative me-1 inline-flex size-11 shrink-0 items-center justify-center rounded-md outline-none hover:bg-card hover:text-card-foreground focus-visible:ring-3 focus-visible:ring-ring/50 md:size-9 after:pointer-events-none after:absolute after:end-0 after:bottom-full after:z-20 after:mb-1.5 after:rounded-md after:bg-foreground after:px-2 after:py-1 after:text-xs after:font-normal after:whitespace-nowrap after:text-background after:opacity-0 after:transition-opacity after:content-[attr(data-tip)] hover:after:opacity-100 focus-visible:after:opacity-100"
          [attr.aria-label]="'Add students to ' + c.name"
          [attr.data-tip]="'Add students to ' + c.name"
          (click)="addStudentsDialog(c)"
        >
          <ng-icon name="lucideUserPlus" size="18" aria-hidden="true" />
        </button>
      } @else {
        <button
          type="button"
          class="me-1 shrink-0 inline-flex size-11 items-center justify-center rounded-md text-muted-foreground transition-colors outline-none hover:bg-muted hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 aria-expanded:bg-muted aria-expanded:text-foreground md:size-8"
          [hlmDropdownMenuTrigger]="cohortMenu"
          align="end"
          [attr.aria-label]="'Actions for ' + c.name"
          data-testid="cohort-actions-menu"
        >
          <ng-icon name="lucideEllipsisVertical" size="18" aria-hidden="true" />
        </button>
        <ng-template #cohortMenu>
          <hlm-dropdown-menu class="w-48">
            @for (item of cohortItemsMenu; track item.key) {
              <button
                type="button"
                hlmDropdownMenuItem
                class="min-h-11 md:min-h-8"
                [variant]="item.destructive ? 'destructive' : 'default'"
                [attr.data-testid]="item.testId"
                (triggered)="onCohortAction(c, item.key)"
              >
                {{ item.label }}
              </button>
            }
          </hlm-dropdown-menu>
        </ng-template>
      }
    </ng-template>
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
  /** A student was enrolled or a cohort changed: the track's history has a new entry. */
  readonly enrolled = output<void>();
  /** The server refused a change and the message was shown: reload the track. */
  readonly refused = output<void>();

  private readonly api = inject(Api);
  private readonly dialog = inject(HlmDialogService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly currentUser = inject(CurrentUserService);
  /** The user page needs USERS_READ: without it the name stays plain text. */
  protected readonly canOpenUsers = computed(() => this.currentUser.hasPermission('USERS_READ'));

  protected readonly PAGE_SIZE = PAGE_SIZE;
  protected readonly headerItems = HEADER_ITEMS;
  protected readonly cohortItemsMenu = COHORT_ITEMS;
  protected readonly assignItem = ASSIGN_ITEM;
  protected readonly studentsText = studentsText;

  /** Shows the cohorts and the enrollments as they are now. */
  reload(): void {
    this.cohorts.reload();
    this.enrollments.reload();
    this.allTotal.reload();
  }

  protected readonly canChange = computed(
    () => this.canEnroll() && this.trackStatus() === 'ACTIVE',
  );

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly pageIndex = computed(() => {
    const value = Number(this.params().get(PAGE_PARAM));
    return Number.isInteger(value) && value > 0 ? value : 0;
  });
  private readonly cohortParam = computed(() => this.params().get(COHORT_PARAM) || null);

  protected readonly cohorts = resource({
    params: () => this.trackId(),
    loader: async ({ params }) => (await this.api.invoke(listTrackCohorts, { id: params })).items,
  });
  /** The last loaded cohorts: they stay on screen while a reload runs. */
  protected readonly cohortItems = linkedSignal<Cohort[] | undefined, Cohort[]>({
    source: () => (this.cohorts.hasValue() ? this.cohorts.value() : undefined),
    computation: (items, previous) => items ?? previous?.value ?? NO_COHORTS,
  });
  protected readonly hasCohorts = computed(() => this.cohortItems().length > 0);
  /** The cohort of the URL; one that no longer exists counts as none. */
  protected readonly selectedCohort = computed(
    () => this.cohortItems().find((c) => c.id === this.cohortParam()) ?? null,
  );
  /** Until the cohorts are known the URL's cohort is trusted, so the first load is not repeated. */
  private readonly cohortFilter = computed(() =>
    this.cohorts.hasValue()
      ? (this.selectedCohort()?.id ?? undefined)
      : (this.cohortParam() ?? undefined),
  );

  protected readonly enrollments = resource({
    params: () => ({ id: this.trackId(), page: this.pageIndex(), cohortId: this.cohortFilter() }),
    loader: ({ params }) =>
      this.api.invoke(listTrackEnrollments, {
        id: params.id,
        page: params.page,
        size: PAGE_SIZE,
        cohortId: params.cohortId,
      }),
  });
  /** The last loaded page: it stays on screen while the next one loads. */
  protected readonly loaded = linkedSignal<
    TrackEnrollmentPage | undefined,
    TrackEnrollmentPage | undefined
  >({
    source: () => (this.enrollments.hasValue() ? this.enrollments.value() : undefined),
    computation: (page, previous) => page ?? previous?.value,
  });

  protected readonly rowItems = computed(
    () =>
      new Map<string, ActionMenuItem[]>(
        (this.loaded()?.items ?? []).map((e) => [
          e.id,
          e.cohort
            ? [
                ASSIGN_ITEM,
                {
                  key: 'take-out',
                  label: `Take out of ${e.cohort.name}`,
                  testId: 'enrollment-menu-take-out',
                  destructive: true,
                },
              ]
            : [ASSIGN_ITEM],
        ]),
      ),
  );

  /** The size of the unfiltered list, asked only while a cohort is chosen. */
  private readonly allTotal = resource({
    params: () => (this.cohortFilter() === undefined ? undefined : this.trackId()),
    loader: async ({ params }) =>
      (await this.api.invoke(listTrackEnrollments, { id: params, page: 0, size: 1 })).totalItems,
  });
  /** All the track's enrollments, those in no cohort included. */
  protected readonly totalEnrollments = linkedSignal<number | undefined, number>({
    source: () =>
      this.cohortFilter() === undefined
        ? this.loaded()?.totalItems
        : this.allTotal.hasValue()
          ? this.allTotal.value()
          : undefined,
    computation: (n, previous) => n ?? previous?.value ?? 0,
  });

  protected rowFrame(on: boolean): string {
    return `flex items-center rounded-lg border ${
      on
        ? 'border-primary border-s-4 bg-accent text-accent-foreground'
        : 'border-transparent hover:bg-muted/60'
    }`;
  }

  protected subtle(on: boolean): string {
    return on ? 'text-accent-foreground' : 'text-muted-foreground';
  }

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

  protected choose(id: string | null): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { [COHORT_PARAM]: id, [PAGE_PARAM]: null },
      queryParamsHandling: 'merge',
    });
  }

  protected onHeaderAction(key: string): void {
    if (key === 'create') {
      this.createCohortDialog();
    } else {
      this.enroll();
    }
  }

  protected onCohortAction(cohort: Cohort, key: string): void {
    if (key === 'add') {
      this.addStudentsDialog(cohort);
    } else {
      void this.deleteCohortDialog(cohort);
    }
  }

  protected onRowAction(e: TrackEnrollment, key: string): void {
    if (key === 'assign') {
      void this.assignMentor(e.id, e.student.name);
    } else if (e.cohort) {
      void this.takeOut(e, e.cohort);
    }
  }

  private candidateLoader(cohort: { id: string; name: string } | null): CandidateLoader {
    const id = this.trackId();
    const cohortId = cohort?.id ?? null;
    return async (q, withoutCohort, page) => {
      const params = { id, q: q || undefined, withoutCohort, page, size: PICKER_PAGE_SIZE };
      try {
        return cohortId
          ? await this.api.invoke(listCohortCandidates, { ...params, cohortId })
          : await this.api.invoke(listNewCohortCandidates, params);
      } catch (error) {
        if (this.explainRefusal(error, cohort ?? undefined)) {
          return null;
        }
        throw error;
      }
    };
  }

  protected createCohortDialog(): void {
    const id = this.trackId();
    const data: CohortStudentsDialogData = {
      mode: 'create',
      load: this.candidateLoader(null),
      save: async (value: CohortFormValue): Promise<boolean | CohortFormErrors> => {
        try {
          const created = await this.api.invoke(createCohort, {
            id,
            body: {
              name: value.name,
              startDate: value.startDate,
              enrollmentIds: value.candidates.map((c) => c.id),
            },
          });
          this.say(`Cohort ${created.name} created`, { duration: 5000, politeness: 'polite' });
          this.afterChange(created.id);
          return true;
        } catch (error) {
          if (this.explainRefusal(error)) {
            return true;
          }
          const problem = problemOf(error);
          if (problem?.type === PROBLEM.validationFailed) {
            const errors: CohortFormErrors = {
              name: problem.errors.find((e) => e.field === 'name')?.message,
              startDate: problem.errors.find((e) => e.field === 'startDate')?.message,
            };
            if (errors.name || errors.startDate) {
              return errors;
            }
          }
          if (problem?.status !== 403) {
            this.say('Could not create the cohort. Try again.');
          }
          return false;
        }
      },
    };
    this.dialog.open(CohortStudentsDialog, { contentClass: 'sm:max-w-[640px]', context: data });
  }

  protected addStudentsDialog(cohort: Cohort): void {
    const id = this.trackId();
    const data: CohortStudentsDialogData = {
      mode: 'add',
      cohortName: cohort.name,
      load: this.candidateLoader(cohort),
      save: async (value: CohortFormValue) => {
        try {
          await this.api.invoke(addCohortEnrollments, {
            id,
            cohortId: cohort.id,
            body: { enrollmentIds: value.candidates.map((c) => c.id) },
          });
          const n = value.candidates.length;
          this.say(`${studentsText(n)} added to ${cohort.name}`, {
            duration: 5000,
            politeness: 'polite',
          });
          this.afterChange(null);
          return true;
        } catch (error) {
          if (this.explainRefusal(error, cohort)) {
            return true;
          }
          if (problemOf(error)?.status !== 403) {
            this.say('Could not add the students. Try again.');
          }
          return false;
        }
      },
    };
    this.dialog.open(CohortStudentsDialog, { contentClass: 'sm:max-w-[640px]', context: data });
  }

  private async takeOut(e: TrackEnrollment, cohort: { id: string; name: string }): Promise<void> {
    const ref = this.dialog.open<boolean, ConfirmDialogData>(ConfirmDialog, {
      contentClass: 'sm:max-w-[420px]',
      context: {
        title: `Take ${e.student.name} out of ${cohort.name}?`,
        text: `${e.student.name} stays enrolled on this track, in no cohort.`,
        confirmLabel: 'Take out',
        destructive: true,
        testIds: { dialog: 'take-out-dialog', confirm: 'take-out-confirm' },
      },
    });
    if (!(await firstValueFrom(ref.closed$))) {
      return;
    }
    try {
      await this.api.invoke(removeCohortEnrollment, {
        id: this.trackId(),
        cohortId: cohort.id,
        enrollmentId: e.id,
      });
    } catch (error) {
      if (!this.explainRefusal(error, cohort) && problemOf(error)?.status !== 403) {
        this.say('Could not take the student out of the cohort. Try again.');
      }
      return;
    }
    this.say(`${e.student.name} taken out of ${cohort.name}`, {
      duration: 5000,
      politeness: 'polite',
    });
    this.afterChange(null);
  }

  private async deleteCohortDialog(cohort: Cohort): Promise<void> {
    const ref = this.dialog.open<boolean, ConfirmDialogData>(ConfirmDialog, {
      contentClass: 'sm:max-w-[420px]',
      context: {
        title: `Delete ${cohort.name}?`,
        text: 'The cohort has no students. Its entries in the history stay.',
        confirmLabel: 'Delete',
        destructive: true,
        testIds: { dialog: 'delete-cohort-dialog', confirm: 'delete-cohort-confirm' },
      },
    });
    if (!(await firstValueFrom(ref.closed$))) {
      return;
    }
    try {
      await this.api.invoke(deleteCohort, { id: this.trackId(), cohortId: cohort.id });
    } catch (error) {
      if (!this.explainRefusal(error, cohort) && problemOf(error)?.status !== 403) {
        this.say('Could not delete the cohort. Try again.');
      }
      return;
    }
    this.say(`${cohort.name} deleted`, { duration: 5000, politeness: 'polite' });
    this.afterChange(null);
  }

  /** The cohorts changed: reload them and the list (a new cohort is chosen), and tell the page about the history. */
  private afterChange(choose: string | null): void {
    this.cohorts.reload();
    if (choose && choose !== this.selectedCohort()?.id) {
      this.choose(choose);
    } else {
      this.enrollments.reload();
    }
    this.enrolled.emit();
  }

  protected enroll(): void {
    const id = this.trackId();
    const data: EnrollStudentDialogData = {
      cohorts: this.cohortItems(),
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
      enroll: async (studentId, startDate, cohortId) => {
        try {
          await this.api.invoke(enrollStudent, {
            id,
            body: { studentId, startDate, ...(cohortId ? { cohortId } : {}) },
          });
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
        this.reload();
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

  /** Explains a 409 and shows the cohorts and the enrollments as they are now; false for any other error. */
  private explainRefusal(error: unknown, cohort?: { id: string; name: string }): boolean {
    const problem = problemOf(error);
    const type = problem?.type;
    if (type === PROBLEM.trackArchived) {
      this.say(
        "This track is archived, so its students and cohorts can't be changed. It is shown as it is now.",
        { politeness: 'assertive' },
      );
    } else if (type === PROBLEM.studentNotEligible) {
      this.say(
        'That student can no longer be enrolled: they must be active, hold the student role and have no active enrollment.',
        { politeness: 'assertive' },
      );
    } else if (type === PROBLEM.studentAlreadyEnrolled) {
      this.say('That student is already enrolled on a track.', { politeness: 'assertive' });
    } else if (type === PROBLEM.cohortNotEligible) {
      this.say('That cohort is no longer on this track. The cohorts are shown as they are now.', {
        politeness: 'assertive',
      });
    } else if (type === PROBLEM.enrollmentNotEligible) {
      this.say(
        'One of the chosen students is no longer enrolled on this track, so nothing was saved. The cohorts and the enrollments are shown as they are now.',
        { politeness: 'assertive' },
      );
    } else if (type === PROBLEM.cohortNotEmpty) {
      this.say(
        `${cohort?.name ?? 'The cohort'} cannot be deleted: a student was added to it. The cohorts are shown as they are now.`,
        { politeness: 'assertive' },
      );
    } else if (problem?.status === 404 && cohort) {
      this.say(`${cohort.name} no longer exists. The cohorts are shown as they are now.`, {
        politeness: 'assertive',
      });
    } else {
      return false;
    }
    this.reload();
    this.refused.emit();
    return true;
  }
}
