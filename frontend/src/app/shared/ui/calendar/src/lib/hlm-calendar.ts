import { NgTemplateOutlet } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  input,
  signal,
} from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideChevronLeft, lucideChevronRight } from '@ng-icons/lucide';
import { BrnCalendar, BrnCalendarImports, injectBrnCalendarI18n } from '@spartan-ng/brain/calendar';
import { injectDateAdapter } from '@spartan-ng/brain/date-time';
import { buttonVariants, HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmSelectImports } from '@spartan-ng/helm/select';
import { provideEnglishCalendar } from '../../../../date/calendar-i18n';
import { classes, hlm } from '@spartan-ng/helm/utils';

const YEARS_BEFORE = 5;
const YEARS_AFTER = 2;
const monthName = (month: number, style: 'long' | 'short') =>
  new Date(2000, month).toLocaleDateString('en-US', { month: style });

@Component({
  selector: 'hlm-calendar',
  exportAs: 'hlmCalendar',
  imports: [BrnCalendarImports, NgIcon, HlmSelectImports, NgTemplateOutlet, HlmButtonImports],
  providers: [provideEnglishCalendar()],
  viewProviders: [provideIcons({ lucideChevronLeft, lucideChevronRight })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  hostDirectives: [
    {
      directive: BrnCalendar,
      inputs: [
        'min',
        'max',
        'disabled',
        'date',
        'dateDisabled',
        'weekStartsOn',
        'highlightDays',
        'defaultFocusedDate',
      ],
      outputs: ['dateChange'],
    },
  ],
  host: { 'data-slot': 'calendar', '(keydown.escape)': '_close($event)' },
  template: `
    <div class="inline-flex w-[calc(var(--cell-size)*7)] flex-col gap-4">
      <!-- Header -->
      <div class="flex w-full items-center justify-between gap-1.5">
        <ng-template #month>
          <hlm-select brnCalendarMonthSelect class="order-1">
            <hlm-select-trigger size="sm" [class]="_selectClass">
              <hlm-select-value />
            </hlm-select-trigger>
            <hlm-select-content *hlmSelectPortal class="max-h-80">
              <hlm-select-group>
                @for (month of _i18n.config().months(); track month) {
                  <hlm-select-item [value]="month">{{ month }}</hlm-select-item>
                }
              </hlm-select-group>
            </hlm-select-content>
          </hlm-select>
        </ng-template>
        <ng-template #year>
          <hlm-select brnCalendarYearSelect class="order-3">
            <hlm-select-trigger size="sm" [class]="_selectClass">
              <hlm-select-value />
            </hlm-select-trigger>
            <hlm-select-content *hlmSelectPortal class="max-h-80">
              <hlm-select-group>
                @for (year of _i18n.config().years(); track year) {
                  <hlm-select-item [value]="year">{{ year }}</hlm-select-item>
                }
              </hlm-select-group>
            </hlm-select-content>
          </hlm-select>
        </ng-template>
        @let heading = _heading();

        <button
          brnCalendarPreviousButton
          [class.invisible]="_view() !== 'days'"
          variant="ghost"
          hlmBtn
          class="order-first size-(--cell-size) p-0 select-none aria-disabled:opacity-50"
        >
          <ng-icon name="lucideChevronLeft" class="rtl:rotate-180" />
        </button>

        @switch (captionLayout()) {
          @case ('dropdown') {
            <ng-container [ngTemplateOutlet]="month" />
            <ng-container [ngTemplateOutlet]="year" />
          }
          @case ('dropdown-months') {
            <ng-container [ngTemplateOutlet]="month" />
            <div brnCalendarHeader class="order-4 text-sm font-medium">{{ heading.year }}</div>
          }
          @case ('dropdown-years') {
            <div brnCalendarHeader class="order-2 text-sm font-medium">{{ heading.month }}</div>
            <ng-container [ngTemplateOutlet]="year" />
          }
          @case ('label') {
            <div brnCalendarHeader class="order-5 flex items-center gap-1 text-sm font-medium">
              <button
                #monthButton
                type="button"
                hlmBtn
                variant="ghost"
                size="sm"
                class="px-2 hover:bg-accent hover:text-accent-foreground dark:hover:bg-accent"
                [attr.aria-expanded]="_view() === 'months'"
                (click)="_show('months')"
              >
                {{ heading.longMonth }}
              </button>
              <button
                #yearButton
                type="button"
                hlmBtn
                variant="ghost"
                size="sm"
                class="px-2 hover:bg-accent hover:text-accent-foreground dark:hover:bg-accent"
                [attr.aria-expanded]="_view() === 'years'"
                (click)="_show('years')"
              >
                {{ heading.year }}
              </button>
            </div>
          }
        }

        <button
          brnCalendarNextButton
          [class.invisible]="_view() !== 'days'"
          hlmBtn
          variant="ghost"
          class="order-last size-(--cell-size) p-0 select-none aria-disabled:opacity-50"
        >
          <ng-icon name="lucideChevronRight" class="rtl:rotate-180" />
        </button>
      </div>

      <!-- The three views share one cell, so the calendar keeps the size of the largest and switching only fades. -->
      <div class="grid min-h-[calc(var(--cell-size)*6+3rem+1.45rem)]">
        <div
          class="col-start-1 row-start-1 transition-[opacity,scale,visibility] duration-150 motion-reduce:transition-none"
          [class]="_viewClass('days')"
          [attr.inert]="_view() === 'days' ? null : ''"
        >
          <table class="w-full border-collapse space-y-1" brnCalendarGrid>
            <thead aria-hidden="true">
              <tr class="flex">
                <th
                  *brnCalendarWeekday="let weekday"
                  scope="col"
                  class="text-muted-foreground flex-1 rounded-(--cell-radius) text-[0.8rem] font-normal select-none"
                  [attr.aria-label]="_i18n.config().labelWeekday(weekday)"
                >
                  {{ _i18n.config().formatWeekdayName(weekday) }}
                </th>
              </tr>
            </thead>

            <tbody role="rowgroup">
              <tr *brnCalendarWeek="let week" class="mt-2 flex w-full">
                @for (date of week; track _dateAdapter.getTime(date)) {
                  <td
                    brnCalendarCell
                    class="group/day relative aspect-square h-full w-full rounded-(--cell-radius) p-0 text-center select-none [&:first-child[data-selected=true]_button]:rounded-s-(--cell-radius) [&:last-child[data-selected=true]_button]:rounded-e-(--cell-radius)"
                  >
                    <button brnCalendarCellButton [date]="date" [class]="_btnClass">
                      {{ _dateAdapter.getDate(date) }}
                    </button>
                  </td>
                }
              </tr>
            </tbody>
          </table>
        </div>
        <div
          class="grid grid-cols-3 content-start gap-2 col-start-1 row-start-1 transition-[opacity,scale,visibility] duration-150 motion-reduce:transition-none"
          [class]="_viewClass('months')"
          [attr.inert]="_view() === 'months' ? null : ''"
          role="group"
          aria-label="Choose a month"
          (keydown)="_navigate($event)"
          (focusin)="_follow($event)"
        >
          @for (month of _months(); track month.index) {
            <button
              type="button"
              [class]="month.class"
              [attr.data-current]="month.current ? '' : null"
              [attr.tabindex]="_tab($index, month.current)"
              [attr.data-today]="month.today ? '' : null"
              [attr.aria-pressed]="month.current"
              (click)="_pickMonth(month.index)"
            >
              {{ month.label }}
            </button>
          }
        </div>
        <div
          class="grid grid-cols-3 content-start gap-2 col-start-1 row-start-1 transition-[opacity,scale,visibility] duration-150 motion-reduce:transition-none"
          [class]="_viewClass('years')"
          [attr.inert]="_view() === 'years' ? null : ''"
          role="group"
          aria-label="Choose a year"
          (keydown)="_navigate($event)"
          (focusin)="_follow($event)"
        >
          @for (year of _years(); track year.value) {
            <button
              type="button"
              [class]="year.class"
              [attr.data-current]="year.current ? '' : null"
              [attr.tabindex]="_tab($index, year.current)"
              [attr.data-today]="year.today ? '' : null"
              [attr.aria-pressed]="year.current"
              (click)="_pickYear(year.value)"
            >
              {{ year.value }}
            </button>
          }
        </div>
      </div>
    </div>
  `,
})
export class HlmCalendar<T> {
  /** Access the calendar i18n */
  protected readonly _i18n = injectBrnCalendarI18n();

  /** Access the date time adapter */
  protected readonly _dateAdapter = injectDateAdapter<T>();

  /** Show dropdowns to navigate between months or years. */
  public readonly captionLayout = input<
    'dropdown' | 'label' | 'dropdown-months' | 'dropdown-years'
  >('label');

  /** Access the calendar directive */
  private readonly _calendar = inject(BrnCalendar);

  /** Get the heading for the current month and year */
  protected readonly _heading = computed(() => {
    const config = this._i18n.config();
    const date = this._calendar.focusedDate();

    return {
      header: config.formatHeader(
        this._dateAdapter.getMonth(date),
        this._dateAdapter.getYear(date),
      ),
      month: config.formatMonth(this._dateAdapter.getMonth(date)),
      longMonth: monthName(this._dateAdapter.getMonth(date), 'long'),
      year: config.formatYear(this._dateAdapter.getYear(date)),
    };
  });

  /** What the calendar shows: the days, or a grid to choose the month or the year in place of them. */
  protected readonly _view = signal<'days' | 'months' | 'years'>('days');
  private readonly _host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly _injector = inject(Injector);

  protected readonly _months = computed(() => {
    const focused = this._calendar.focusedDate();
    const current = this._dateAdapter.getMonth(focused);
    const focusedYear = this._dateAdapter.getYear(focused);
    const now = this._dateAdapter.now();
    const todayMonth = this._dateAdapter.getMonth(now);
    const todayYear = this._dateAdapter.getYear(now);
    return Array.from({ length: 12 }, (_, index) => ({
      index,
      label: monthName(index, 'short'),
      current: index === current,
      class: this._gridClass(index === current, index === todayMonth && focusedYear === todayYear),
      today: index === todayMonth && focusedYear === todayYear,
    }));
  });

  /** From 5 years before the current year to 2 after it. */
  protected readonly _years = computed(() => {
    const now = this._dateAdapter.getYear(this._dateAdapter.now());
    const current = this._dateAdapter.getYear(this._calendar.focusedDate());
    return Array.from({ length: YEARS_BEFORE + YEARS_AFTER + 1 }, (_, i) => {
      const value = now - YEARS_BEFORE + i;
      return {
        value,
        current: value === current,
        today: value === now,
        class: this._gridClass(value === current, value === now),
      };
    });
  });

  // Plain buttons with the whole class set: on an `hlmBtn` a bound `[class]` is dropped by the library's class manager.
  private _gridClass(current: boolean, today: boolean): string {
    if (current) return hlm(buttonVariants({ variant: 'default' }));
    return hlm(buttonVariants({ variant: 'ghost' }), this._hoverClass, today && this._todayClass);
  }

  protected _viewClass(view: 'days' | 'months' | 'years'): string {
    return this._view() === view ? 'opacity-100 scale-100' : 'invisible opacity-0 scale-95';
  }

  protected _show(view: 'months' | 'years'): void {
    this._view.set(this._view() === view ? 'days' : view);
    this._roving.set(null);
    if (this._view() === view) {
      afterNextRender(
        () => {
          const grid = this._host.nativeElement.querySelector<HTMLElement>(
            `[aria-label="${view === 'months' ? 'Choose a month' : 'Choose a year'}"]`,
          );
          // The grid is `visibility: hidden` until its fade has started, and the fade starts with the next
          // style calculation: an element cannot take the focus before, so one frame is waited, then the fade.
          requestAnimationFrame(() => {
            const fading = (grid?.getAnimations?.() ?? []).map((a) => a.finished);
            void Promise.allSettled(fading).then(() =>
              grid?.querySelector<HTMLElement>('[data-current]')?.focus(),
            );
          });
        },
        { injector: this._injector },
      );
    }
  }

  protected _pickMonth(month: number): void {
    const year = this._dateAdapter.getYear(this._calendar.focusedDate());
    this._calendar.focusedDate.set(this._dateAdapter.create({ year, month, day: 1 }));
    this._back('months');
  }

  protected _pickYear(year: number): void {
    const month = this._dateAdapter.getMonth(this._calendar.focusedDate());
    this._calendar.focusedDate.set(this._dateAdapter.create({ year, month, day: 1 }));
    this._back('years');
  }

  /** The item of the open grid that is the Tab stop: the one that has the focus, or else the displayed one. */
  private readonly _roving = signal<number | null>(null);

  protected _tab(index: number, current: boolean): 0 | -1 {
    return (this._roving() ?? (current ? index : -1)) === index ? 0 : -1;
  }

  protected _follow(event: FocusEvent): void {
    const items = [...(event.currentTarget as HTMLElement).querySelectorAll('button')];
    const at = items.indexOf(event.target as HTMLButtonElement);
    if (at >= 0) this._roving.set(at);
  }

  /** Arrow keys, Home and End move the focus through a grid of three columns and stop at its edges. */
  protected _navigate(event: KeyboardEvent): void {
    const steps: Record<string, number> = {
      ArrowLeft: -1,
      ArrowRight: 1,
      ArrowUp: -3,
      ArrowDown: 3,
    };
    const items = [...(event.currentTarget as HTMLElement).querySelectorAll<HTMLElement>('button')];
    const at = items.indexOf(event.target as HTMLElement);
    if (at < 0) return;
    let next: number;
    if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = items.length - 1;
    else if (event.key in steps) next = at + steps[event.key];
    else return;
    event.preventDefault();
    if (next >= 0 && next < items.length) items[next].focus();
  }

  protected _close(event: Event): void {
    // Escape closes a grid, wherever the focus is, not the popup around the calendar; in the days it goes on to the popup.
    if (this._view() === 'days') return;
    event.stopPropagation();
    this._back(this._view() === 'years' ? 'years' : 'months');
  }

  /** Back to the days, with the focus on the header button that opened the grid. */
  private _back(from: 'months' | 'years'): void {
    this._view.set('days');
    this._roving.set(null);
    afterNextRender(
      () => {
        const buttons = this._host.nativeElement.querySelectorAll<HTMLElement>(
          '[brnCalendarHeader] button',
        );
        buttons[from === 'months' ? 0 : 1]?.focus();
      },
      { injector: this._injector },
    );
  }

  protected readonly _btnClass = hlm(
    buttonVariants({ variant: 'ghost', size: 'icon' }),
    'data-[today=true]:bg-muted group-data-[focused=true]/day:border-ring group-data-[focused=true]/day:ring-ring/50 data-[range-end=true]:bg-primary data-[range-end=true]:text-primary-foreground data-[range-middle=true]:bg-muted data-[range-middle=true]:text-foreground data-[range-start=true]:bg-primary data-[range-start=true]:text-primary-foreground data-[selected-single=true]:bg-primary data-[selected-single=true]:text-primary-foreground hover:bg-accent hover:text-accent-foreground dark:hover:bg-accent dark:hover:text-accent-foreground relative isolate z-10 flex aspect-square size-auto w-full min-w-(--cell-size) flex-col gap-1 border-0 leading-none font-normal group-data-[focused=true]/day:relative group-data-[focused=true]/day:z-10 group-data-[focused=true]/day:ring-[3px] data-[range-end=true]:rounded-(--cell-radius) data-[range-end=true]:rounded-e-(--cell-radius) data-[range-middle=true]:rounded-none data-[range-start=true]:rounded-(--cell-radius) data-[range-start=true]:rounded-s-(--cell-radius) [&>span]:text-xs [&>span]:opacity-70',
    'data-[outside=true]:opacity-50',
    "data-[highlighted]:before:content-['']",
    'data-[highlighted]:before:absolute',
    'data-[highlighted]:before:bottom-1',
    'data-[highlighted]:before:start-1/2',
    'data-[highlighted]:before:h-1',
    'data-[highlighted]:before:w-1',
    'data-[highlighted]:before:-translate-x-1/2',
    'data-[highlighted]:before:rounded-full',
    'data-[highlighted]:before:bg-destructive',
  );

  protected readonly _hoverClass =
    'hover:bg-accent hover:text-accent-foreground dark:hover:bg-accent';
  /** The quiet mark of the current month or year, as the mark of today among the days. */
  protected readonly _todayClass = 'bg-muted ring-1 ring-inset ring-border';
  protected readonly _selectClass = 'gap-0 px-1.5 py-2 [&>ng-icon]:ms-1';

  constructor() {
    classes(
      () =>
        'p-3 [--cell-radius:var(--radius-md)] [--cell-size:--spacing(8)] group/calendar bg-background block in-data-[slot=card-content]:bg-transparent in-data-[slot=popover-content]:bg-transparent',
    );
  }
}
