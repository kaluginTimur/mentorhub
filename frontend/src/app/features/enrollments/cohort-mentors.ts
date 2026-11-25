import {
  Component,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideChevronDown, lucideTriangleAlert, lucideUserMinus } from '@ng-icons/lucide';
import type { BrnOverlayState } from '@spartan-ng/brain/overlay';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmPopoverImports } from '@spartan-ng/helm/popover';
import { CohortMentor } from '../../core/api/models/cohort-mentor';
import { BelowHeaderScrolls } from '../../shared/popover/below-header-scroll';
import { Avatar } from '../../shared/avatar/avatar';
import { Email } from '../../shared/email/email';
import { AssignmentMark } from '../tracks/assignment-mark';
import {
  activeFirst,
  fitCount,
  isInactive,
  markWords,
  mentorsText,
  summaryText,
} from './cohort-mentors-text';

/** The icon button that removes a mentor; its tooltip says "Remove" and its name carries the object. */
export const REMOVE_MENTOR_BUTTON_CLASS =
  "relative shrink-0 border-destructive/40 bg-card text-destructive hover:bg-destructive/10 hover:text-mh-destructive-hover-foreground dark:border-destructive/50 dark:bg-card dark:hover:bg-destructive/20 after:pointer-events-none after:absolute after:right-0 after:bottom-full after:z-20 after:mb-1.5 after:rounded-md after:bg-foreground after:px-2 after:py-1 after:text-xs after:font-normal after:text-background after:opacity-0 after:transition-opacity after:content-['Remove'] hover:after:opacity-100 focus-visible:after:opacity-100";

export interface CohortRef {
  id: string;
  name: string;
}

/** The cards of the mentors of a cohort: name, email, the marks as words, and the removal for who may change. */
@Component({
  selector: 'app-cohort-mentor-cards',
  imports: [Avatar, Email, AssignmentMark, HlmButton, NgIcon],
  providers: [provideIcons({ lucideUserMinus })],
  host: { class: 'block' },
  template: `
    <ul class="divide-y divide-border">
      @for (m of mentors(); track m.id) {
        <li class="flex items-center gap-3 py-2 first:pt-0 last:pb-0">
          <app-avatar [user]="{ id: m.id, email: m.email, firstName: m.name }" size="sm" />
          <div class="flex min-w-0 flex-1 flex-col gap-1">
            <span class="font-medium break-words">{{ m.name }}</span>
            <span class="font-mono text-xs break-all text-muted-foreground"
              ><app-email [value]="m.email"
            /></span>
            @if (inactive(m)) {
              <div class="flex flex-wrap gap-1.5 pt-0.5">
                @if (m.disabled) {
                  <app-assignment-mark label="Disabled" />
                }
                @if (!m.mentorRole) {
                  <app-assignment-mark label="No mentor role" />
                }
              </div>
            }
          </div>
          @if (canRemove()) {
            <button
              hlmBtn
              variant="outline"
              size="icon-sm"
              type="button"
              [class]="removeClass"
              [attr.aria-label]="'Remove ' + m.name + ' as a mentor of ' + cohortName()"
              (click)="remove.emit(m)"
            >
              <ng-icon name="lucideUserMinus" aria-hidden="true" />
              <span class="sr-only">Remove</span>
            </button>
          }
        </li>
      }
    </ul>
  `,
})
export class CohortMentorCards {
  readonly mentors = input.required<readonly CohortMentor[]>();
  readonly cohortName = input.required<string>();
  readonly canRemove = input.required<boolean>();
  readonly remove = output<CohortMentor>();

  protected readonly removeClass = REMOVE_MENTOR_BUTTON_CLASS;
  protected readonly inactive = isInactive;
}

/**
 * The borderless button of a cohort's mentors (avatars, the count, a chevron) with a popover of all of them.
 * It does not choose the cohort (COH-8).
 */
@Component({
  selector: 'app-cohort-mentors-button',
  imports: [HlmPopoverImports, NgIcon, Avatar, CohortMentorCards],
  providers: [provideIcons({ lucideChevronDown, lucideTriangleAlert })],
  host: { class: 'block min-w-0' },
  template: `
    <hlm-popover
      [scrollStrategy]="scrolls.for('button')"
      [attachTo]="host"
      align="start"
      sideOffset="4"
      [state]="state()"
      (stateChanged)="state.set($event)"
    >
      <button
        hlmPopoverTrigger
        type="button"
        class="flex min-h-11 w-fit max-w-full min-w-0 items-center gap-1.5 rounded-lg py-1 ps-1.5 pe-2 text-start text-xs text-muted-foreground outline-none hover:bg-secondary hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 aria-expanded:bg-secondary aria-expanded:text-foreground md:min-h-8"
        [attr.aria-label]="'Mentors of ' + cohort().name + ': ' + summary()"
      >
        <span class="inline-flex items-center" aria-hidden="true">
          @for (m of stack(); track m.id; let first = $first; let i = $index) {
            <app-avatar
              [user]="{ id: m.id, email: m.email, firstName: m.name }"
              [pixelSize]="26"
              [class]="
                onItem() ? 'relative outline-1 outline-accent' : 'relative outline-1 outline-card'
              "
              [style.z-index]="stack().length - i"
              [class.-ms-1.5]="!first"
            />
          }
        </span>
        <span class="min-w-0 truncate">{{ summary() }}</span>
        @if (inactiveCount() > 0) {
          <ng-icon
            name="lucideTriangleAlert"
            size="14"
            class="shrink-0 !text-mh-warning-soft-foreground"
            aria-hidden="true"
          />
        }
        <ng-icon name="lucideChevronDown" size="14" class="shrink-0" aria-hidden="true" />
      </button>
      <hlm-popover-content
        *hlmPopoverPortal
        class="max-h-[inherit] w-80 max-w-[calc(100vw-2rem)] overflow-y-auto p-3"
        [attr.aria-label]="'Mentors of ' + cohort().name"
      >
        <h3 class="mb-2 text-sm font-semibold">Mentors of {{ cohort().name }}</h3>
        <app-cohort-mentor-cards
          [mentors]="ordered()"
          [cohortName]="cohort().name"
          [canRemove]="canRemove()"
          (remove)="state.set('closed'); remove.emit($event)"
        />
      </hlm-popover-content>
    </hlm-popover>
  `,
})
export class CohortMentorsButton {
  readonly cohort = input.required<CohortRef & { mentors: CohortMentor[] }>();
  readonly canRemove = input.required<boolean>();
  /** The button sits in the chosen item of the Cohorts list, so the avatars' rings take its fill. */
  readonly onItem = input(false);
  readonly remove = output<CohortMentor>();

  protected readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  protected readonly scrolls = new BelowHeaderScrolls();
  protected readonly state = signal<BrnOverlayState>('closed');
  protected readonly ordered = computed(() => activeFirst(this.cohort().mentors));
  protected readonly stack = computed(() => this.ordered().slice(0, 3));
  protected readonly summary = computed(() => summaryText(this.cohort().mentors));
  protected readonly inactiveCount = computed(
    () => this.cohort().mentors.filter(isInactive).length,
  );
}

/**
 * The chosen cohort's mentors above the enrollments (COH-8): names that open a card, "+k" for those that do not
 * fit in one row, and on a phone one button with the count.
 */
@Component({
  selector: 'app-cohort-mentors-line',
  imports: [HlmPopoverImports, NgIcon, CohortMentorCards, CohortMentorsButton],
  providers: [provideIcons({ lucideTriangleAlert })],
  host: { class: 'block' },
  template: `
    @if (mentors().length === 0) {
      <p class="flex flex-wrap items-center gap-x-1.5 text-sm text-muted-foreground">
        <span class="font-semibold text-foreground">Mentors:</span>
        <span
          >No mentors in {{ cohort().name }}. Its students are led only by the mentors of their
          enrollments.</span
        >
      </p>
    } @else {
      <div
        #group
        role="group"
        [attr.aria-label]="'Mentors of ' + cohort().name"
        class="relative flex min-h-6 items-center gap-1.5 text-sm text-muted-foreground"
      >
        <span #label class="shrink-0 font-semibold text-foreground">Mentors:</span>
        <div class="hidden min-w-0 flex-1 items-center md:flex">
          @for (m of visible(); track m.id; let last = $last) {
            <hlm-popover
              [scrollStrategy]="scrolls.for('name:' + m.id)"
              align="start"
              sideOffset="6"
              [state]="stateOf('name:' + m.id)"
              (stateChanged)="onState('name:' + m.id, $event)"
            >
              <button
                hlmPopoverTrigger
                type="button"
                [class]="nameClass"
                [attr.aria-label]="m.name + marks(m)"
              >
                <span class="truncate">{{ m.name }}</span>
                @if (inactive(m)) {
                  <ng-icon
                    name="lucideTriangleAlert"
                    size="14"
                    class="shrink-0 !text-mh-warning-soft-foreground"
                    aria-hidden="true"
                  />
                }
              </button>
              <hlm-popover-content
                *hlmPopoverPortal
                class="max-h-[inherit] w-80 max-w-[calc(100vw-2rem)] overflow-y-auto p-3"
                [attr.aria-label]="m.name"
              >
                <app-cohort-mentor-cards
                  [mentors]="[m]"
                  [cohortName]="cohort().name"
                  [canRemove]="canRemove()"
                  (remove)="forward($event)"
                />
              </hlm-popover-content>
            </hlm-popover>
            @if (!last || rest().length > 0) {
              <span class="whitespace-pre" aria-hidden="true">, </span>
            }
          }
          @if (rest().length > 0) {
            <hlm-popover
              [scrollStrategy]="scrolls.for('more')"
              align="start"
              sideOffset="6"
              [state]="stateOf('more')"
              (stateChanged)="onState('more', $event)"
            >
              <button
                hlmPopoverTrigger
                type="button"
                [class]="nameClass"
                [attr.aria-label]="moreLabel()"
              >
                +{{ rest().length }}
                @if (restInactive() > 0) {
                  <ng-icon
                    name="lucideTriangleAlert"
                    size="14"
                    class="shrink-0 !text-mh-warning-soft-foreground"
                    aria-hidden="true"
                  />
                }
              </button>
              <hlm-popover-content
                *hlmPopoverPortal
                class="max-h-[inherit] w-80 max-w-[calc(100vw-2rem)] overflow-y-auto p-3"
                [attr.aria-label]="'More mentors of ' + cohort().name"
              >
                <app-cohort-mentor-cards
                  [mentors]="rest()"
                  [cohortName]="cohort().name"
                  [canRemove]="canRemove()"
                  (remove)="forward($event)"
                />
              </hlm-popover-content>
            </hlm-popover>
          }
        </div>
        <app-cohort-mentors-button
          class="flex-1 md:hidden"
          [cohort]="cohort()"
          [canRemove]="canRemove()"
          (remove)="remove.emit($event)"
        />
        <div
          #probe
          aria-hidden="true"
          class="pointer-events-none invisible absolute top-0 left-0 flex h-0 w-max items-center overflow-hidden whitespace-nowrap"
        >
          @for (m of ordered(); track m.id) {
            <span data-name [class]="probeClass">
              <span>{{ m.name }}</span>
              @if (inactive(m)) {
                <span class="w-3.5 shrink-0"></span>
              }
              <span>,&nbsp;</span>
            </span>
          }
          <span data-more [class]="probeClass">
            <span>+{{ ordered().length }}</span
            ><span class="w-3.5 shrink-0"></span>
          </span>
        </div>
      </div>
    }
  `,
})
export class CohortMentorsLine {
  readonly cohort = input.required<CohortRef & { mentors: CohortMentor[] }>();
  readonly canRemove = input.required<boolean>();
  readonly remove = output<CohortMentor>();

  protected readonly nameClass =
    'relative inline-flex min-h-6 max-w-full min-w-0 items-center gap-1.5 rounded-sm text-start text-sm text-muted-foreground outline-none hover:text-foreground hover:underline focus-visible:ring-2 focus-visible:ring-ring aria-expanded:text-foreground aria-expanded:underline';
  protected readonly probeClass = 'inline-flex min-h-6 shrink-0 items-center gap-1.5 text-sm';
  protected readonly inactive = isInactive;
  protected readonly marks = markWords;

  protected readonly mentors = computed(() => this.cohort().mentors);
  protected readonly ordered = computed(() => activeFirst(this.mentors()));
  private readonly shown = signal(Number.POSITIVE_INFINITY);
  protected readonly visible = computed(() => this.ordered().slice(0, this.shown()));
  protected readonly rest = computed(() => this.ordered().slice(this.shown()));
  protected readonly restInactive = computed(() => this.rest().filter(isInactive).length);
  protected readonly moreLabel = computed(() => {
    const rest = this.rest().length;
    const inactive = this.restInactive();
    return `${mentorsText(rest).replace('mentor', 'more mentor')} of ${this.cohort().name}${inactive > 0 ? `, ${inactive} inactive` : ''}`;
  });

  protected readonly scrolls = new BelowHeaderScrolls();
  private readonly open = signal<string | null>(null);
  private readonly group = viewChild<ElementRef<HTMLElement>>('group');
  private readonly label = viewChild<ElementRef<HTMLElement>>('label');
  private readonly probe = viewChild<ElementRef<HTMLElement>>('probe');

  constructor() {
    afterRenderEffect(() => {
      this.ordered();
      this.measure();
    });
    afterRenderEffect((onCleanup) => {
      const group = this.group()?.nativeElement;
      if (group && typeof ResizeObserver !== 'undefined') {
        const observer = new ResizeObserver(() => this.measure());
        observer.observe(group);
        onCleanup(() => observer.disconnect());
      }
    });
  }

  /** The names that fit in the row, from the widths of an invisible copy of all of them. */
  private measure(): void {
    const group = this.group()?.nativeElement;
    const probe = this.probe()?.nativeElement;
    const label = this.label()?.nativeElement;
    if (!group || !probe || !label) {
      return;
    }
    const widths = [...probe.querySelectorAll<HTMLElement>('[data-name]')].map(
      (e) => e.offsetWidth,
    );
    const more = probe.querySelector<HTMLElement>('[data-more]')?.offsetWidth ?? 0;
    const available = group.clientWidth - label.offsetWidth - 6;
    this.shown.set(
      available > 0 && widths.some((w) => w > 0)
        ? fitCount(widths, more, available)
        : Number.POSITIVE_INFINITY,
    );
  }

  protected stateOf(key: string): BrnOverlayState {
    return this.open() === key ? 'open' : 'closed';
  }

  protected onState(key: string, state: BrnOverlayState): void {
    if (state === 'open') {
      this.open.set(key);
    } else if (this.open() === key) {
      this.open.set(null);
    }
  }

  protected forward(mentor: CohortMentor): void {
    this.open.set(null);
    this.remove.emit(mentor);
  }
}
