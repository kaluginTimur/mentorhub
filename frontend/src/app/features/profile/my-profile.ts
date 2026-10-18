import { Component, computed, inject } from '@angular/core';
import { Api } from '../../core/api/api';
import { updateMyProfile } from '../../core/api/fn/users/update-my-profile';
import { ProfileUpdate } from '../../core/api/models/profile-update';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { ProfileForm, ProfileValues } from '../../shared/profile-form/profile-form';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'app-my-profile',
  imports: [ProfileForm],
  template: `
    <main>
      <h1>Profile</h1>
      @if (values(); as current) {
        <app-profile-form [values]="current" [save]="save" (saved)="onSaved()" />
      }
    </main>
  `,
  styles: `
    main {
      padding: 24px 16px;
      max-width: 640px;
      margin: 0 auto;
    }
    h1 {
      font: var(--mat-sys-headline-medium);
      margin: 0 0 16px;
    }
  `,
})
export class MyProfile {
  private readonly api = inject(Api);
  private readonly currentUser = inject(CurrentUserService);
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
