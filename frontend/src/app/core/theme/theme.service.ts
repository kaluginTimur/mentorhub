import { Service, signal } from '@angular/core';

export type ThemeChoice = 'system' | 'light' | 'dark';

export const THEME_STORAGE_KEY = 'mentorhub.theme';

const CHOICES: readonly ThemeChoice[] = ['system', 'light', 'dark'];

/** The color scheme choice: the system's by default, kept in this browser only. */
@Service()
export class ThemeService {
  readonly choice = signal<ThemeChoice>(this.restore());

  constructor() {
    this.apply(this.choice());
  }

  set(choice: ThemeChoice): void {
    this.choice.set(choice);
    this.apply(choice);
    try {
      if (choice === 'system') localStorage.removeItem(THEME_STORAGE_KEY);
      else localStorage.setItem(THEME_STORAGE_KEY, choice);
    } catch {
      // storage is blocked: the choice lasts until the page is closed
    }
  }

  private restore(): ThemeChoice {
    try {
      const stored = localStorage.getItem(THEME_STORAGE_KEY);
      return CHOICES.find((c) => c === stored) ?? 'system';
    } catch {
      return 'system';
    }
  }

  // light-dark() follows the color-scheme property of <html>; without an inline value the stylesheet's `light dark`
  // (the system's scheme) applies.
  private apply(choice: ThemeChoice): void {
    document.documentElement.style.colorScheme = choice === 'system' ? '' : choice;
  }
}
