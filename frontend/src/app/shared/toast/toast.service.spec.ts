import { TestBed } from '@angular/core/testing';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders a polite status toast with a Dismiss action and removes it on click', () => {
    const service = TestBed.inject(ToastService);
    service.show('Profile saved');
    TestBed.tick();

    const status = document.body.querySelector('app-toast-host [role="status"]');
    expect(status?.getAttribute('aria-live')).toBe('polite');
    expect(status?.textContent).toContain('Profile saved');

    const button = status?.querySelector('button') as HTMLButtonElement | null;
    expect(button?.textContent?.trim()).toBe('Dismiss');

    button?.click();
    TestBed.tick();
    expect(document.body.querySelector('app-toast-host [role="status"]')).toBeNull();
  });

  it('renders an assertive alert toast for conflicts', () => {
    const service = TestBed.inject(ToastService);
    service.show("You can't lock yourself out.", { politeness: 'assertive' });
    TestBed.tick();

    const alert = document.body.querySelector('app-toast-host [role="alert"]');
    expect(alert?.getAttribute('aria-live')).toBe('assertive');
    expect(alert?.textContent).toContain("You can't lock yourself out.");
  });

  it('auto-dismisses after the requested duration', () => {
    vi.useFakeTimers();
    const service = TestBed.inject(ToastService);
    service.show('Link copied', { duration: 5000, politeness: 'polite' });
    TestBed.tick();

    expect(document.body.querySelector('app-toast-host [role="status"]')).not.toBeNull();

    vi.advanceTimersByTime(5000);
    TestBed.tick();

    expect(document.body.querySelector('app-toast-host [role="status"]')).toBeNull();
  });

  it('caps visible toasts and supports dismissAll', () => {
    const service = TestBed.inject(ToastService);
    service.show('First');
    service.show('Second');
    service.show('Third');
    service.show('Fourth');
    TestBed.tick();

    const items = [...document.body.querySelectorAll('app-toast-host [role="status"]')];
    expect(items).toHaveLength(3);
    expect(items.map((el) => el.textContent)).not.toContain('First');
    expect(items[2]?.textContent).toContain('Fourth');

    service.dismissAll();
    TestBed.tick();
    expect(document.body.querySelectorAll('app-toast-host [role="status"]')).toHaveLength(0);
  });
});
