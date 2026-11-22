import { DatePipe } from '@angular/common';
import {
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideChevronUp } from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { Api } from '../../core/api/api';
import { listTrackEnrollments } from '../../core/api/fn/enrollments/list-track-enrollments';
import { TrackEnrollment } from '../../core/api/models/track-enrollment';
import { Avatar } from '../../shared/avatar/avatar';
import { DATE_FORMAT } from '../../shared/date/date-format';
import { Email } from '../../shared/email/email';
import { AssignmentMark } from './assignment-mark';

const PAGE_SIZE = 100;
/** More students than this get the foot with the way back to the top. */
const LONG_LIST = 8;

/**
 * The students a mentor leads on a track, under the mentor's row (ENR-7). All of them are loaded,
 * following the pages, and shown in a scroll area.
 */
@Component({
  selector: 'app-mentor-students',
  imports: [DatePipe, NgIcon, HlmButton, Avatar, Email, AssignmentMark],
  providers: [provideIcons({ lucideChevronUp })],
  host: { class: 'block' },
  template: `
    <div
      class="grid grid-cols-[2.5rem_minmax(0,1fr)] grid-rows-[auto_auto_auto] sm:grid-cols-[3rem_minmax(0,1fr)]"
    >
      <div
        aria-hidden="true"
        class="hidden bg-primary/15 sm:col-start-1 sm:row-span-3 sm:row-start-1 sm:block dark:bg-primary/22"
      ></div>
      @if (failed()) {
        <div
          class="col-span-2 row-start-1 flex flex-wrap border-t border-border items-center gap-3 px-3 py-3 sm:col-span-1 sm:col-start-2"
        >
          <p role="alert" class="text-sm text-destructive">
            Could not load the students of {{ mentorName() }}.
          </p>
          <button hlmBtn variant="outline" size="sm" type="button" (click)="reload()">Retry</button>
        </div>
      } @else if (loaded() === null) {
        <div
          class="col-span-2 row-start-1 border-t border-border px-3 py-3 text-sm text-muted-foreground sm:col-span-1 sm:col-start-2"
        >
          <div
            role="progressbar"
            [attr.aria-label]="'Loading the students of ' + mentorName()"
            class="h-1 w-full overflow-hidden rounded-full bg-primary/15"
          >
            <div class="animate-mh-indeterminate h-full w-1/3 rounded-full bg-primary"></div>
          </div>
          <p class="mt-2">Loading the students of {{ mentorName() }}...</p>
        </div>
      } @else if (items().length === 0) {
        <p
          class="col-span-2 row-start-1 border-t border-border px-3 py-3.5 text-sm text-muted-foreground sm:col-span-1 sm:col-start-2"
        >
          {{ mentorName() }} leads no students on this track.
        </p>
      } @else {
        <div
          tabindex="0"
          role="region"
          [attr.aria-label]="'Students of ' + mentorName()"
          class="col-span-2 row-start-2 border-t border-border max-h-[min(26rem,55dvh)] min-w-0 overflow-auto focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-mh-highlight-soft-foreground sm:col-span-1 sm:col-start-2 sm:max-h-[26rem] dark:focus-visible:outline-ring"
        >
          <table class="w-full border-collapse text-sm max-sm:block sm:table-fixed">
            <colgroup>
              <col class="sm:w-[36%]" />
              <col />
              <col class="sm:w-[120px]" />
            </colgroup>
            <thead class="max-sm:hidden">
              <tr>
                <th
                  scope="col"
                  class="sticky top-0 z-[1] bg-muted py-1.5 pr-3 pl-4 text-left font-semibold"
                >
                  Student
                </th>
                <th
                  scope="col"
                  class="sticky top-0 z-[1] bg-muted px-3 py-1.5 text-left font-semibold"
                >
                  Email
                </th>
                <th
                  scope="col"
                  class="sticky top-0 z-[1] bg-muted px-3 py-1.5 text-left font-semibold"
                >
                  Started
                </th>
              </tr>
            </thead>
            <tbody class="max-sm:block">
              @for (e of items(); track e.id) {
                <tr
                  class="border-t border-border first:border-t-0 max-sm:block max-sm:px-3 max-sm:py-2"
                >
                  <td class="py-1.5 pr-3 pl-4 align-middle max-sm:block max-sm:p-0">
                    <div class="flex items-center gap-3 max-sm:items-start">
                      <app-avatar
                        [user]="{
                          id: e.student.id,
                          email: e.student.email,
                          firstName: e.student.name,
                        }"
                        size="xs"
                        class="max-sm:mt-0.5"
                      />
                      <span class="min-w-0">
                        <span class="font-medium break-words">{{ e.student.name }}</span>
                        @if (e.student.disabled || !e.student.studentRole) {
                          <span
                            class="inline-flex flex-wrap items-center gap-1.5 align-middle max-sm:mt-0.5 max-sm:flex sm:ml-1.5"
                          >
                            @if (e.student.disabled) {
                              <app-assignment-mark label="Disabled" />
                            }
                            @if (!e.student.studentRole) {
                              <app-assignment-mark label="No student role" />
                            }
                          </span>
                        }
                      </span>
                    </div>
                  </td>
                  <td
                    class="px-3 py-1.5 align-middle font-mono text-xs break-words text-muted-foreground max-sm:block max-sm:px-0 max-sm:py-0 max-sm:pl-9"
                  >
                    <app-email [value]="e.student.email" />
                  </td>
                  <td
                    class="px-3 py-1.5 align-middle whitespace-nowrap text-muted-foreground max-sm:block max-sm:px-0 max-sm:py-0 max-sm:pl-9 max-sm:text-xs max-sm:before:content-['Started_']"
                  >
                    <time [attr.datetime]="e.startDate">{{ e.startDate | date: dateFormat }}</time>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (items().length > longList) {
          <button
            type="button"
            class="col-start-1 row-start-3 flex min-h-[52px] items-center justify-center self-stretch border-t border-border bg-primary/15 text-accent-foreground hover:bg-primary/23 focus-visible:outline-2 focus-visible:-outline-offset-4 focus-visible:outline-mh-highlight-soft-foreground sm:border-t-0 sm:bg-transparent sm:hover:bg-primary/10 dark:bg-primary/22 dark:hover:bg-primary/34 dark:focus-visible:outline-ring dark:sm:bg-transparent dark:sm:hover:bg-primary/15"
            [attr.aria-label]="'Hide the students of ' + mentorName() + ' (back to top)'"
            (click)="hide.emit()"
          >
            <ng-icon name="lucideChevronUp" size="16" aria-hidden="true" />
          </button>
          <p
            class="col-start-2 row-start-3 flex min-h-[52px] items-center border-t border-border px-3 py-1.5 text-sm text-muted-foreground"
          >
            End of the list: all {{ items().length }} students shown
          </p>
        }
      }
    </div>
  `,
})
export class MentorStudents {
  protected readonly dateFormat = DATE_FORMAT;
  protected readonly longList = LONG_LIST;
  readonly trackId = input.required<string>();
  readonly mentorId = input.required<string>();
  readonly mentorName = input.required<string>();
  /** The bottom chevron: the list should be hidden. */
  readonly hide = output<void>();

  private readonly api = inject(Api);

  // Loaded by hand, not with resource(): the list loads on request and must not hold the page unstable.
  protected readonly loaded = signal<TrackEnrollment[] | null>(null);
  protected readonly failed = signal(false);
  private request = 0;
  protected readonly items = computed(() => this.loaded() ?? []);

  constructor() {
    effect(() => {
      this.trackId();
      this.mentorId();
      untracked(() => {
        this.loaded.set(null);
        void this.load();
      });
    });
  }

  /** Shows the students as they are now; the list on screen stays until the answer comes. */
  reload(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    const request = ++this.request;
    this.failed.set(false);
    try {
      const items: TrackEnrollment[] = [];
      for (let page = 0; ; page++) {
        const result = await this.api.invoke(listTrackEnrollments, {
          id: this.trackId(),
          mentor: this.mentorId(),
          page,
          size: PAGE_SIZE,
        });
        items.push(...result.items);
        if (result.items.length === 0 || items.length >= result.totalItems) {
          break;
        }
      }
      if (request === this.request) {
        this.loaded.set(items);
      }
    } catch {
      if (request === this.request) {
        this.failed.set(true);
      }
    }
  }
}
