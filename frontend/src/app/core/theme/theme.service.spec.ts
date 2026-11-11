import { TestBed } from '@angular/core/testing';
import { THEME_STORAGE_KEY, ThemeService } from './theme.service';

describe('ThemeService', () => {
  afterEach(() => {
    localStorage.clear();
    document.documentElement.style.colorScheme = '';
    vi.restoreAllMocks();
  });

  it('follows the system scheme when nothing is stored', () => {
    const service = TestBed.inject(ThemeService);
    expect(service.choice()).toBe('system');
    expect(document.documentElement.style.colorScheme).toBe('');
  });

  it('keeps a choice, applies it to the whole page and restores it', () => {
    TestBed.inject(ThemeService).set('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');

    TestBed.resetTestingModule();
    document.documentElement.style.colorScheme = '';
    const restored = TestBed.inject(ThemeService);
    expect(restored.choice()).toBe('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');

    restored.set('system');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBeNull();
    expect(document.documentElement.style.colorScheme).toBe('');
  });

  it('ignores an unknown stored value', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'purple');
    expect(TestBed.inject(ThemeService).choice()).toBe('system');
  });

  it('works when the browser storage is blocked', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const service = TestBed.inject(ThemeService);
    expect(service.choice()).toBe('system');
    service.set('light');
    expect(service.choice()).toBe('light');
    expect(document.documentElement.style.colorScheme).toBe('light');
  });
});
