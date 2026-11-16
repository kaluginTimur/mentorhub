import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ScrollIntoViewOnChange } from './scroll-into-view-on-change';

@Component({
  imports: [ScrollIntoViewOnChange],
  template: `<div id="card" [appScrollIntoViewOnChange]="page()"></div>`,
})
class Host {
  readonly page = signal(0);
}

describe('ScrollIntoViewOnChange', () => {
  const setup = async (top: number) => {
    const scrollIntoView = vi.fn();
    Element.prototype.scrollIntoView = scrollIntoView;
    const fixture = TestBed.createComponent(Host);
    const card = (fixture.nativeElement as HTMLElement).querySelector('#card') as HTMLElement;
    card.getBoundingClientRect = () => ({ top }) as DOMRect;
    await fixture.whenStable();
    return { fixture, scrollIntoView };
  };

  it('does not scroll on the first value', async () => {
    const { scrollIntoView } = await setup(-200);
    expect(scrollIntoView).not.toHaveBeenCalled();
  });

  it('scrolls when the value changes and the element is above the visible area', async () => {
    const { fixture, scrollIntoView } = await setup(-200);
    fixture.componentInstance.page.set(1);
    await fixture.whenStable();
    expect(scrollIntoView).toHaveBeenCalledOnce();
  });

  it('does not scroll when the element is already visible', async () => {
    const { fixture, scrollIntoView } = await setup(120);
    fixture.componentInstance.page.set(1);
    await fixture.whenStable();
    expect(scrollIntoView).not.toHaveBeenCalled();
  });
});
