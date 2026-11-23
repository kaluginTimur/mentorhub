import { Component, ElementRef, computed, inject, input, model, signal } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCalendar } from '@ng-icons/lucide';
import type { BrnOverlayState } from '@spartan-ng/brain/overlay';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCalendar } from '@spartan-ng/helm/calendar';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmPopoverImports } from '@spartan-ng/helm/popover';

export const toIsoDate = (date: Date): string => {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
};

/** A real calendar date typed as `yyyy-mm-dd`, in the user's time zone; otherwise undefined. */
export function parseIsoDate(text: string): Date | undefined {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text.trim());
  if (!match) {
    return undefined;
  }
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
    ? date
    : undefined;
}

/** A date typed as `yyyy-mm-dd` with a calendar popup; the label and the error text belong to the form. */
@Component({
  selector: 'app-date-field',
  imports: [NgIcon, HlmButton, HlmInput, HlmCalendar, HlmPopoverImports],
  providers: [provideIcons({ lucideCalendar })],
  host: { class: 'relative block' },
  template: `
    <input
      hlmInput
      type="text"
      inputmode="numeric"
      autocomplete="off"
      placeholder="yyyy-mm-dd"
      class="pr-10"
      [id]="inputId()"
      [value]="value()"
      [attr.data-testid]="inputTestId() ?? null"
      [attr.aria-invalid]="invalid() ? 'true' : null"
      [attr.aria-describedby]="describedBy() ?? null"
      (input)="value.set($any($event.target).value)"
      (change)="value.set($any($event.target).value)"
    />
    <hlm-popover
      align="end"
      sideOffset="4"
      [state]="calendarState()"
      (stateChanged)="calendarState.set($event)"
    >
      <button
        hlmBtn
        hlmPopoverTrigger
        variant="ghost"
        size="icon-sm"
        type="button"
        class="absolute top-1/2 right-0.5 -translate-y-1/2"
        aria-label="Choose the start date in a calendar"
      >
        <ng-icon name="lucideCalendar" aria-hidden="true" />
      </button>
      <hlm-popover-content
        class="w-fit p-0"
        *hlmPopoverPortal
        (keydown.tab)="onCalendarTab($event)"
        (keydown.shift.tab)="onCalendarTab($event)"
      >
        <hlm-calendar
          class="p-3"
          [date]="picked()"
          [defaultFocusedDate]="picked() ?? undefined"
          (dateChange)="pick($event)"
        />
      </hlm-popover-content>
    </hlm-popover>
  `,
})
export class DateField {
  readonly inputId = input.required<string>();
  readonly inputTestId = input<string>();
  readonly invalid = input(false);
  readonly describedBy = input<string>();
  /** The typed text, `yyyy-mm-dd` when it is a date. */
  readonly value = model('');

  protected readonly calendarState = signal<BrnOverlayState | null>(null);
  protected readonly picked = computed(() => parseIsoDate(this.value()));
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected pick(date: Date | undefined): void {
    if (date) {
      this.value.set(toIsoDate(date));
    }
    this.calendarState.set('closed');
  }

  /**
   * The calendar renders in an overlay outside the dialog, so Tab from its edge would reach the page
   * behind the modal: it closes the popup and returns the focus to the date field instead.
   */
  protected onCalendarTab(event: Event): void {
    const keyboard = event as KeyboardEvent;
    const popup = event.currentTarget as HTMLElement;
    const stops = Array.from(popup.querySelectorAll<HTMLElement>('button, [tabindex]')).filter(
      (el) =>
        el.tabIndex >= 0 &&
        !(el as HTMLButtonElement).disabled &&
        !el.closest('[inert]') &&
        getComputedStyle(el).visibility !== 'hidden',
    );
    const edge = keyboard.shiftKey ? stops[0] : stops[stops.length - 1];
    if (stops.length > 0 && document.activeElement !== edge) {
      return;
    }
    keyboard.preventDefault();
    this.calendarState.set('closed');
    this.host.nativeElement.querySelector<HTMLInputElement>('input')?.focus();
  }
}
