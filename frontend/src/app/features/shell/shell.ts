import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { AuthService } from '../../core/auth/auth.service';
import { CurrentUserService } from '../../core/auth/current-user.service';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatMenuModule,
    MatIconModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly currentUser = inject(CurrentUserService);

  protected readonly name = this.currentUser.name;
  protected readonly canReadUsers = computed(() => this.currentUser.hasPermission('USERS_READ'));
  protected readonly canInvite = computed(() => this.currentUser.hasPermission('USERS_INVITE'));
  protected readonly email = computed(() => this.currentUser.user()?.email ?? '');

  protected signOut(): void {
    this.auth.signOut();
  }
}
