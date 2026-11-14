import { DOCUMENT } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { THEME_STORAGE_KEY, ThemeService } from './theme.service';

interface MockMediaQueryList {
  matches: boolean;
  media: string;
  addEventListener: ReturnType<typeof vi.fn>;
  removeEventListener: ReturnType<typeof vi.fn>;
  dispatchChange: (matches: boolean) => void;
}

describe('ThemeService', () => {
  let originalMatchMediaDescriptor: PropertyDescriptor | undefined;

  function installMatchMedia(initialMatches: boolean): MockMediaQueryList {
    const listeners = new Set<(event: MediaQueryListEvent) => void>();
    const list: MockMediaQueryList = {
      matches: initialMatches,
      media: '(prefers-color-scheme: dark)',
      addEventListener: vi.fn((_type: string, listener: (event: MediaQueryListEvent) => void) => {
        listeners.add(listener);
      }),
      removeEventListener: vi.fn(
        (_type: string, listener: (event: MediaQueryListEvent) => void) => {
          listeners.delete(listener);
        },
      ),
      dispatchChange: (matches: boolean) => {
        list.matches = matches;
        const event = { matches, media: list.media } as MediaQueryListEvent;
        for (const listener of listeners) {
          listener(event);
        }
      },
    };

    const win = TestBed.inject(DOCUMENT).defaultView;
    if (win) {
      Object.defineProperty(win, 'matchMedia', {
        value: () => list as unknown as MediaQueryList,
        configurable: true,
        writable: true,
      });
    }
    return list;
  }

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    document.documentElement.style.colorScheme = '';
    const win = TestBed.inject(DOCUMENT).defaultView;
    originalMatchMediaDescriptor = win
      ? Object.getOwnPropertyDescriptor(win, 'matchMedia')
      : undefined;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    const win = TestBed.inject(DOCUMENT).defaultView;
    if (win) {
      if (originalMatchMediaDescriptor) {
        Object.defineProperty(win, 'matchMedia', originalMatchMediaDescriptor);
      } else {
        delete (win as unknown as { matchMedia?: unknown }).matchMedia;
      }
    }
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    document.documentElement.style.colorScheme = '';
  });

  it('defaults to system preference when storage is empty and follows matchMedia', () => {
    const list = installMatchMedia(true);

    const service = TestBed.inject(ThemeService);

    expect(service.preference()).toBe('system');
    expect(service.resolved()).toBe('dark');
    expect(service.isDark()).toBe(true);
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(document.documentElement.style.colorScheme).toBe('dark');

    list.dispatchChange(false);
    TestBed.tick();

    expect(service.resolved()).toBe('light');
    expect(service.isDark()).toBe(false);
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(document.documentElement.style.colorScheme).toBe('light');
  });

  it('restores a valid stored preference from localStorage', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'dark');
    installMatchMedia(false);

    const service = TestBed.inject(ThemeService);

    expect(service.preference()).toBe('dark');
    expect(service.resolved()).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('falls back to system when stored preference is invalid', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'sepia');
    installMatchMedia(false);

    const service = TestBed.inject(ThemeService);

    expect(service.preference()).toBe('system');
    expect(service.resolved()).toBe('light');
  });

  it('persists preference changes and ignores system changes while explicit theme is chosen', () => {
    const list = installMatchMedia(false);

    const service = TestBed.inject(ThemeService);

    service.setPreference('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(document.documentElement.style.colorScheme).toBe('dark');

    list.dispatchChange(false);
    TestBed.tick();
    expect(service.resolved()).toBe('dark');

    service.setPreference('light');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(document.documentElement.style.colorScheme).toBe('light');

    list.dispatchChange(true);
    service.setPreference('system');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('system');
    expect(service.resolved()).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('handles missing matchMedia gracefully in non-browser environments', () => {
    const win = TestBed.inject(DOCUMENT).defaultView;
    if (win) {
      Object.defineProperty(win, 'matchMedia', {
        value: undefined,
        configurable: true,
        writable: true,
      });
    }

    const service = TestBed.inject(ThemeService);

    expect(service.preference()).toBe('system');
    expect(service.resolved()).toBe('light');
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(document.documentElement.style.colorScheme).toBe('light');
  });
});
