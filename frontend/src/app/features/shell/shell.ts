import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideChevronDown,
  lucideLogOut,
  lucideMenu,
  lucideMonitor,
  lucideMoon,
  lucideSun,
  lucideUser,
  lucideX,
} from '@ng-icons/lucide';
import { HlmDropdownMenuImports } from '@spartan-ng/helm/dropdown-menu';
import { AuthService } from '../../core/auth/auth.service';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { ThemePreference, ThemeService } from '../../core/theme/theme.service';
import { Avatar } from '../../shared/avatar/avatar';

const THEME_ICONS: Record<ThemePreference, string> = {
  light: 'lucideSun',
  dark: 'lucideMoon',
  system: 'lucideMonitor',
};

const THEME_LABELS: Record<ThemePreference, string> = {
  light: 'Light',
  dark: 'Dark',
  system: 'System',
};

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, HlmDropdownMenuImports, NgIcon, Avatar],
  providers: [
    provideIcons({
      lucideSun,
      lucideMoon,
      lucideMonitor,
      lucideChevronDown,
      lucideUser,
      lucideLogOut,
      lucideMenu,
      lucideX,
    }),
  ],
  templateUrl: './shell.html',
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly currentUser = inject(CurrentUserService);
  private readonly theme = inject(ThemeService);

  protected readonly user = this.currentUser.user;
  protected readonly name = this.currentUser.name;
  protected readonly email = computed(() => this.currentUser.user()?.email ?? '');
  protected readonly canReadUsers = computed(() => this.currentUser.hasPermission('USERS_READ'));
  protected readonly canInvite = computed(() => this.currentUser.hasPermission('USERS_INVITE'));
  protected readonly hasNavLinks = computed(() => this.canReadUsers() || this.canInvite());
  protected readonly mobileMenuOpen = signal(false);

  protected readonly themePreference = this.theme.preference;
  protected readonly themeIcon = computed(() => THEME_ICONS[this.themePreference()]);
  protected readonly themeLabel = computed(() => THEME_LABELS[this.themePreference()]);

  protected setTheme(preference: ThemePreference): void {
    this.theme.setPreference(preference);
  }

  protected toggleMobileMenu(): void {
    this.mobileMenuOpen.update((open) => !open);
  }

  protected closeMobileMenu(): void {
    this.mobileMenuOpen.set(false);
  }

  protected signOut(): void {
    this.auth.signOut();
  }
}
