import { DatePipe } from '@angular/common';
import { Component, computed, inject, linkedSignal, resource } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { Api } from '../../core/api/api';
import { listMyStudents } from '../../core/api/fn/enrollments/list-my-students';
import { MentoredEnrollmentPage } from '../../core/api/models/mentored-enrollment-page';
import { DATE_FORMAT } from '../../shared/date/date-format';
import { Email } from '../../shared/email/email';
import { Loading } from '../../shared/loading/loading';
import { Paginator } from '../../shared/paginator/paginator';
import { trackLabel } from '../../shared/track-label/track-label';
import { Unavailable } from '../../shared/unavailable/unavailable';
import { AssignmentMark } from '../tracks/assignment-mark';

const PAGE_SIZE = 25;

/** The enrollments a mentor leads (ENR-12). */
@Component({
  selector: 'app-my-students-page',
  imports: [DatePipe, Email, HlmCardImports, Loading, Unavailable, Paginator, AssignmentMark],
  template: `
    @if (students.error()) {
      <app-unavailable (retry)="students.reload()" />
    } @else if (!loaded()) {
      <app-loading />
    } @else {
      <main class="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-8 sm:px-6">
        <h1 class="font-display text-2xl font-bold tracking-tight">My students</h1>

        @if (loaded(); as page) {
          @if (page.totalItems === 0) {
            <div
              class="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border bg-card px-4 py-12 text-center text-sm text-muted-foreground"
            >
              <p>You do not lead any students yet</p>
            </div>
          } @else {
            <div hlmCard class="relative" [attr.aria-busy]="students.isLoading()">
              <div class="absolute inset-x-(--card-spacing) -top-2 h-1">
                @if (students.isLoading()) {
                  <div
                    role="progressbar"
                    aria-label="Loading students"
                    class="h-1 w-full overflow-hidden rounded-full bg-primary/15"
                  >
                    <div
                      class="animate-mh-indeterminate h-full w-1/3 rounded-full bg-primary"
                    ></div>
                  </div>
                }
              </div>
              <div hlmCardContent>
                @if (page.items.length > 0) {
                  <ul class="divide-y divide-border">
                    @for (e of page.items; track e.id) {
                      <li class="flex flex-wrap items-start justify-between gap-x-4 gap-y-2 py-2.5">
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
                        <div class="flex shrink-0 flex-col items-end gap-1 text-sm">
                          <span class="break-words">{{ label(e.track) }}</span>
                          <span class="text-muted-foreground">
                            From
                            <time [attr.datetime]="e.startDate">{{
                              e.startDate | date: dateFormat
                            }}</time>
                          </span>
                        </div>
                      </li>
                    }
                  </ul>
                } @else {
                  <p class="text-xs text-muted-foreground">No students on this page.</p>
                }
              </div>
              @if (page.totalItems > PAGE_SIZE) {
                <app-paginator
                  [totalItems]="page.totalItems"
                  [pageSize]="PAGE_SIZE"
                  [pageIndex]="pageIndex()"
                  (pageChange)="onPage($event)"
                />
              }
            </div>
          }
        }
      </main>
    }
  `,
})
export class MyStudentsPage {
  protected readonly dateFormat = DATE_FORMAT;
  protected readonly PAGE_SIZE = PAGE_SIZE;
  protected readonly label = trackLabel;
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly pageIndex = computed(() => {
    const value = Number(this.params().get('page'));
    return Number.isInteger(value) && value > 0 ? value : 0;
  });

  protected readonly students = resource({
    params: () => this.pageIndex(),
    loader: ({ params }) => this.api.invoke(listMyStudents, { page: params, size: PAGE_SIZE }),
  });
  /** The last loaded page: it stays on screen while the next one loads. */
  protected readonly loaded = linkedSignal<
    MentoredEnrollmentPage | undefined,
    MentoredEnrollmentPage | undefined
  >({
    source: () => (this.students.hasValue() ? this.students.value() : undefined),
    computation: (page, previous) => page ?? previous?.value,
  });

  protected onPage(index: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: index || null },
      queryParamsHandling: 'merge',
    });
  }
}
