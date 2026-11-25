import { OverlayRef, ScrollDispatcher, ScrollStrategy } from '@angular/cdk/overlay';
import { DOCUMENT } from '@angular/common';
import { NgZone, inject } from '@angular/core';
import { Subscription } from 'rxjs';

/**
 * Keeps a popover attached to its trigger while the page scrolls, and clips it at the bottom edge of the
 * sticky top bar so that it never covers the bar.
 */
class BelowHeaderScrollStrategy implements ScrollStrategy {
  private overlay: OverlayRef | null = null;
  private subscription: Subscription | null = null;

  constructor(
    private readonly scrollDispatcher: ScrollDispatcher,
    private readonly zone: NgZone,
    private readonly document: Document,
  ) {}

  attach(overlayRef: OverlayRef): void {
    this.overlay = overlayRef;
  }

  enable(): void {
    this.subscription ??= this.scrollDispatcher.scrolled(0).subscribe(() => {
      this.zone.run(() => {
        this.overlay?.updatePosition();
        this.clip();
      });
    });
    requestAnimationFrame(() => this.clip());
  }

  disable(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
  }

  detach(): void {
    this.disable();
    this.overlay = null;
  }

  private clip(): void {
    const host = this.overlay?.hostElement;
    const header = this.document.querySelector('[data-site-bar]');
    if (!host || !header) {
      return;
    }
    const cut = Math.max(
      0,
      header.getBoundingClientRect().bottom - host.getBoundingClientRect().top,
    );
    host.style.clipPath = cut > 0 ? `inset(${cut}px -64px -64px -64px)` : '';
  }
}

/** The scroll strategies of the popovers of one component, one per key (a strategy serves one popover). */
export class BelowHeaderScrolls {
  private readonly strategies = new Map<string, ScrollStrategy>();
  private readonly scrollDispatcher = inject(ScrollDispatcher);
  private readonly zone = inject(NgZone);
  private readonly document = inject(DOCUMENT);

  for(key: string): ScrollStrategy {
    let strategy = this.strategies.get(key);
    if (!strategy) {
      strategy = new BelowHeaderScrollStrategy(this.scrollDispatcher, this.zone, this.document);
      this.strategies.set(key, strategy);
    }
    return strategy;
  }
}
