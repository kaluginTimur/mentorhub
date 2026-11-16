import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HlmCalendar } from './hlm-calendar';

@Component({
  imports: [HlmCalendar],
  template: `<hlm-calendar [date]="date" />`,
})
class Host {
  readonly date = new Date(2026, 10, 17);
}

describe('HlmCalendar month and year grids', () => {
  const setup = async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    const header = () => [...el.querySelectorAll<HTMLButtonElement>('[brnCalendarHeader] button')];
    const group = (name: string) => el.querySelector(`[role=group][aria-label="${name}"]`);
    return { fixture, el, header, group };
  };

  it('shows a grid of months in place of the days and returns to the chosen month', async () => {
    const { fixture, el, header, group } = await setup();
    expect(header().map((b) => b.textContent?.trim())).toEqual(['November', '2026']);
    header()[0].click();
    await fixture.whenStable();
    const months = [...(group('Choose a month')?.querySelectorAll('button') ?? [])];
    expect(months.map((b) => b.textContent?.trim())).toEqual([
      'Jan',
      'Feb',
      'Mar',
      'Apr',
      'May',
      'Jun',
      'Jul',
      'Aug',
      'Sep',
      'Oct',
      'Nov',
      'Dec',
    ]);
    expect(el.querySelector('table')?.parentElement?.hasAttribute('inert'), 'days hidden').toBe(
      true,
    );
    months[2].click();
    await fixture.whenStable();
    expect(el.querySelector('table')?.parentElement?.hasAttribute('inert'), 'days back').toBe(
      false,
    );
    expect(header()[0].textContent?.trim()).toBe('March');
  });

  it('offers the years from 5 before to 2 after the current one', async () => {
    const { fixture, header, group } = await setup();
    header()[1].click();
    await fixture.whenStable();
    const years = [...(group('Choose a year')?.querySelectorAll('button') ?? [])].map((b) =>
      Number(b.textContent?.trim()),
    );
    const now = new Date().getFullYear();
    expect(years).toEqual(Array.from({ length: 8 }, (_, i) => now - 5 + i));
  });

  it('closes a grid on Escape, wherever the focus is, and leaves Escape of the days to the popup', async () => {
    const { fixture, el, header, group } = await setup();
    const reached = vi.fn();
    document.addEventListener('keydown', reached);
    const escape = (target: Element) =>
      target.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    try {
      for (const [index, name] of [
        [0, 'Choose a month'],
        [1, 'Choose a year'],
      ] as const) {
        header()[index].click();
        await fixture.whenStable();
        // The focus stays on the header button that opened the grid.
        escape(header()[index]);
        await fixture.whenStable();
        expect(group(name)?.hasAttribute('inert'), `${name} closed`).toBe(true);
        expect(el.querySelector('table')?.parentElement?.hasAttribute('inert')).toBe(false);
      }
      expect(reached, 'the popup does not see Escape of a grid').not.toHaveBeenCalled();
      escape(header()[0]);
      expect(reached, 'the popup sees Escape of the days').toHaveBeenCalledTimes(1);
    } finally {
      document.removeEventListener('keydown', reached);
    }
  });

  it('marks the current month and year when another one is shown', async () => {
    const { fixture, el, header, group } = await setup();
    const now = new Date();
    const marked = (name: string) =>
      [...el.querySelectorAll(`[aria-label="${name}"] [data-today]`)].map((b) =>
        b.textContent?.trim(),
      );
    header()[0].click();
    await fixture.whenStable();
    expect(marked('Choose a month'), 'current year').toEqual([
      new Date(2000, now.getMonth()).toLocaleDateString('en-US', { month: 'short' }),
    ]);
    header()[0].click();
    await fixture.whenStable();
    // Show a month and a year other than the current ones.
    header()[0].click();
    await fixture.whenStable();
    [...(group('Choose a month')?.querySelectorAll('button') ?? [])][
      (now.getMonth() + 1) % 12
    ].click();
    await fixture.whenStable();
    header()[1].click();
    await fixture.whenStable();
    [...(group('Choose a year')?.querySelectorAll('button') ?? [])][0].click();
    await fixture.whenStable();
    header()[0].click();
    await fixture.whenStable();
    expect(
      el.querySelectorAll('[aria-label="Choose a month"] [data-today]'),
      'other year',
    ).toHaveLength(0);
    header()[0].click();
    await fixture.whenStable();
    header()[1].click();
    await fixture.whenStable();
    const today = el.querySelectorAll('[aria-label="Choose a year"] [data-today]');
    expect(today).toHaveLength(1);
    // The mark is in the rendered classes, and the hover of the grid is the accent one.
    expect(today[0].classList.contains('bg-muted')).toBe(true);
    expect(today[0].classList.contains('ring-border')).toBe(true);
    const plain = el.querySelector(
      '[aria-label="Choose a year"] button:not([data-today]):not([data-current])',
    );
    expect(plain?.classList.contains('hover:bg-accent')).toBe(true);
    expect(plain?.classList.contains('hover:bg-muted')).toBe(false);
    expect(plain?.classList.contains('ring-border')).toBe(false);
    expect(today[0].textContent?.trim()).toBe(String(now.getFullYear()));
  });

  it('moves the focus through a grid with the arrow keys and makes it one tab stop', async () => {
    const { fixture, header, group } = await setup();
    const press = (key: string) =>
      (document.activeElement as HTMLElement).dispatchEvent(
        new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }),
      );
    for (const [index, name] of [
      [0, 'Choose a month'],
      [1, 'Choose a year'],
    ] as const) {
      header()[index].click();
      await fixture.whenStable();
      const items = [...(group(name)?.querySelectorAll<HTMLElement>('button') ?? [])];
      const current = items.findIndex((b) => b.hasAttribute('data-current'));
      expect(
        items.filter((b) => b.tabIndex === 0),
        `${name}: one tab stop`,
      ).toEqual([items[current]]);
      await vi.waitFor(() => expect(document.activeElement).toBe(items[current]));
      press('Home');
      expect(document.activeElement).toBe(items[0]);
      await fixture.whenStable();
      expect(
        items.filter((b) => b.tabIndex === 0),
        'the tab stop follows the focus',
      ).toEqual([items[0]]);
      press('ArrowLeft');
      press('ArrowUp');
      expect(document.activeElement, 'stops at the edge').toBe(items[0]);
      press('ArrowRight');
      expect(document.activeElement).toBe(items[1]);
      press('ArrowDown');
      expect(document.activeElement).toBe(items[4]);
      press('ArrowUp');
      expect(document.activeElement).toBe(items[1]);
      press('End');
      expect(document.activeElement).toBe(items[items.length - 1]);
      press('ArrowRight');
      expect(document.activeElement, 'stops at the last').toBe(items[items.length - 1]);
      header()[index].click();
      await fixture.whenStable();
    }
  });
});
