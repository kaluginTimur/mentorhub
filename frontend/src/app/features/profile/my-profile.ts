import { Component, computed, inject } from '@angular/core';
import { Api } from '../../core/api/api';
import { updateMyProfile } from '../../core/api/fn/users/update-my-profile';
import { ProfileUpdate } from '../../core/api/models/profile-update';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { Avatar } from '../../shared/avatar/avatar.component';
import { ProfileForm, ProfileValues } from '../../shared/profile-form/profile-form';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { displayName } from '../../core/auth/user-name';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'app-my-profile',
  imports: [ProfileForm, Avatar, MatCardModule, MatDividerModule],
  template: `
    <main class="page">
      <h1>Profile</h1>
      @if (currentUser.user(); as me) {
        <mat-card appearance="outlined">
          <mat-card-content>
            <div class="header">
              <app-avatar [user]="me" [size]="56" />
              <div class="who">
                <div class="name">{{ displayName(me) }}</div>
                <div class="email">{{ me.email }}</div>
              </div>
            </div>
            <mat-divider />
            @if (values(); as current) {
              <app-profile-form
                [values]="current"
                [save]="save"
                [showEmail]="false"
                (saved)="onSaved()"
              />
            }
          </mat-card-content>
        </mat-card>
      }
    </main>
  `,
  styles: `
    main {
      max-inline-size: 688px;
      margin-inline: auto;
    }
    h1 {
      font: var(--mat-sys-headline-medium);
      margin: 0 0 16px;
    }
    mat-card-content {
      padding-block: 24px;
    }
    .header {
      display: flex;
      align-items: center;
      gap: 16px;
      padding-bottom: 20px;
    }
    .who {
      min-inline-size: 0;
    }
    .name {
      font: var(--mat-sys-title-large);
    }
    .email {
      font: var(--mat-sys-body-medium);
      color: var(--mat-sys-on-surface-variant);
    }
    mat-divider {
      margin-bottom: 24px;
    }
  `,
})
export class MyProfile {
  private readonly api = inject(Api);
  protected readonly displayName = displayName;
  protected readonly currentUser = inject(CurrentUserService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly values = computed<ProfileValues | null>(() => this.currentUser.user());

  protected readonly save = async (body: ProfileUpdate): Promise<ProfileValues> => {
    const me = await this.api.invoke(updateMyProfile, { body });
    this.currentUser.me.set(me);
    return me.user ?? { email: '', ...body };
  };

  protected onSaved(): void {
    this.snackBar.open('Profile saved', 'Dismiss', { duration: 5000, politeness: 'polite' });
  }
}
