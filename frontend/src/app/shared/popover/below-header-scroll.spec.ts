import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { Component, Injector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { BelowHeaderScrolls } from './below-header-scroll';

@Component({ selector: 'app-popover-probe', template: 'popover' })
class PopoverProbe {}

const rect = (top: number, bottom: number) =>
  ({ top, bottom, left: 0, right: 0, width: 0, height: bottom - top, x: 0, y: top }) as DOMRect;

describe('BelowHeaderScrolls', () => {
  let bar: HTMLElement | null;
  let overlayRef: OverlayRef;

  const nextFrame = () => new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));

  const open = (barBottom: number | null, hostTop: number) => {
    if (barBottom !== null) {
      bar = document.createElement('div');
      bar.setAttribute('data-site-bar', '');
      bar.getBoundingClientRect = () => rect(0, barBottom);
      document.body.appendChild(bar);
    }
    const injector = TestBed.inject(Injector);
    const scrolls = runInInjectionContext(injector, () => new BelowHeaderScrolls());
    overlayRef = TestBed.inject(Overlay).create({
      positionStrategy: TestBed.inject(Overlay).position().global(),
      scrollStrategy: scrolls.for('probe'),
    });
    overlayRef.hostElement.getBoundingClientRect = () => rect(hostTop, hostTop + 100);
    overlayRef.attach(new ComponentPortal(PopoverProbe));
  };

  const scrollPage = async () => {
    document.dispatchEvent(new Event('scroll'));
    await nextFrame();
  };
  const clip = () => overlayRef.hostElement.style.clipPath;

  afterEach(() => {
    overlayRef.dispose();
    bar?.remove();
    bar = null;
  });

  it('clips the popover at the bottom edge of the site bar when it reaches under it', async () => {
    open(60, 20);
    await nextFrame();

    expect(clip()).toBe('inset(40px -64px -64px -64px)');
  });

  it('follows the page scroll, and shows the whole popover again when it is clear of the bar', async () => {
    open(60, 20);
    await nextFrame();

    overlayRef.hostElement.getBoundingClientRect = () => rect(50, 150);
    await scrollPage();
    expect(clip()).toBe('inset(10px -64px -64px -64px)');

    overlayRef.hostElement.getBoundingClientRect = () => rect(80, 180);
    await scrollPage();
    expect(clip()).toBe('');
  });

  it('does nothing without a site bar', async () => {
    open(null, 20);
    await nextFrame();
    await scrollPage();

    expect(clip()).toBe('');
  });

  it('stops following the scroll once the popover is closed', async () => {
    open(60, 20);
    await nextFrame();
    const host = overlayRef.hostElement;

    overlayRef.detach();
    host.getBoundingClientRect = () => rect(50, 150);
    await scrollPage();

    expect(host.style.clipPath).toBe('inset(40px -64px -64px -64px)');
  });
});
