import { computed, DestroyRef, DOCUMENT, effect, inject, Service, signal } from '@angular/core';

export type ThemePreference = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

export const THEME_STORAGE_KEY = 'mentorhub.theme';
const DARK_MEDIA_QUERY = '(prefers-color-scheme: dark)';

@Service()
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  private readonly preferenceState = signal<ThemePreference>(this.readStoredPreference());
  private readonly systemPrefersDark = signal(false);

  readonly preference = this.preferenceState.asReadonly();
  readonly resolved = computed<ResolvedTheme>(() => {
    const pref = this.preferenceState();
    if (pref === 'light' || pref === 'dark') {
      return pref;
    }
    return this.systemPrefersDark() ? 'dark' : 'light';
  });
  readonly isDark = computed(() => this.resolved() === 'dark');

  constructor() {
    this.listenToSystemPreference();
    this.applyTheme(this.resolved());

    effect(() => {
      this.applyTheme(this.resolved());
    });
  }

  setPreference(preference: ThemePreference): void {
    this.preferenceState.set(preference);
    this.writeStoredPreference(preference);
    this.applyTheme(this.resolved());
  }

  private applyTheme(theme: ResolvedTheme): void {
    const root = this.document.documentElement;
    root.classList.toggle('dark', theme === 'dark');
    root.style.colorScheme = theme;
  }

  private listenToSystemPreference(): void {
    const win = this.document.defaultView;
    if (!win || typeof win.matchMedia !== 'function') {
      return;
    }
    const mediaQuery = win.matchMedia(DARK_MEDIA_QUERY);
    this.systemPrefersDark.set(Boolean(mediaQuery.matches));

    if (typeof mediaQuery.addEventListener !== 'function') {
      return;
    }
    const onChange = (event: MediaQueryListEvent) => {
      this.systemPrefersDark.set(event.matches);
    };
    mediaQuery.addEventListener('change', onChange);
    this.destroyRef.onDestroy(() => {
      mediaQuery.removeEventListener('change', onChange);
    });
  }

  private readStoredPreference(): ThemePreference {
    try {
      const stored = globalThis.localStorage?.getItem(THEME_STORAGE_KEY);
      if (stored === 'light' || stored === 'dark' || stored === 'system') {
        return stored;
      }
    } catch {
      // Storage may be unavailable in restricted browser contexts.
    }
    return 'system';
  }

  private writeStoredPreference(preference: ThemePreference): void {
    try {
      globalThis.localStorage?.setItem(THEME_STORAGE_KEY, preference);
    } catch {
      // Ignore storage errors so theme toggling still works in memory.
    }
  }
}
