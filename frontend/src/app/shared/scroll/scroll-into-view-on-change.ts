import {
  afterNextRender,
  Directive,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
} from '@angular/core';

/**
 * Brings the element into view when `appScrollIntoViewOnChange` changes (a page of a list), but only
 * if its top is above the visible area; it never scrolls on the first value. The visible area starts
 * below the sticky top bar: give the element `scroll-mt-*` as tall as the bar.
 */
@Directive({ selector: '[appScrollIntoViewOnChange]' })
export class ScrollIntoViewOnChange {
  readonly key = input.required<unknown>({ alias: 'appScrollIntoViewOnChange' });

  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly injector = inject(Injector);
  private initial = true;

  constructor() {
    effect(() => {
      this.key();
      if (this.initial) {
        this.initial = false;
        return;
      }
      afterNextRender(() => this.reveal(), { injector: this.injector });
    });
  }

  private reveal(): void {
    const margin = parseFloat(getComputedStyle(this.element).scrollMarginTop) || 0;
    if (this.element.getBoundingClientRect().top >= margin) {
      return;
    }
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    this.element.scrollIntoView({ behavior: reduced ? 'instant' : 'smooth', block: 'start' });
  }
}
