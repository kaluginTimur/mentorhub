import { Component, computed, inject } from '@angular/core';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { Api } from '../../core/api/api';
import { updateMyProfile } from '../../core/api/fn/users/update-my-profile';
import { ProfileUpdate } from '../../core/api/models/profile-update';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { displayName } from '../../core/auth/user-name';
import { Avatar } from '../../shared/avatar/avatar';
import { ProfileForm, ProfileValues } from '../../shared/profile-form/profile-form';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-my-profile',
  imports: [HlmCardImports, Avatar, ProfileForm],
  template: `
    <main class="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-8 sm:px-6">
      <h1
        #heading
        tabindex="-1"
        class="font-display text-2xl font-bold tracking-tight outline-none"
        [textContent]="'Profile'"
      ></h1>

      @if (values(); as current) {
        <div class="grid gap-6 md:grid-cols-[18rem_1fr] md:items-start lg:grid-cols-[20rem_1fr]">
          <div hlmCard>
            <div hlmCardContent class="flex flex-col items-center gap-4 text-center">
              @if (user(); as profileUser) {
                <app-avatar [user]="profileUser" size="xl" />
              }
              @if (fullName() && fullName() !== email()) {
                <p class="w-full text-base font-semibold break-words text-foreground">
                  {{ fullName() }}
                </p>
              }
              @if (email(); as addr) {
                <div class="w-full space-y-1">
                  <p class="text-xs font-medium text-muted-foreground">Email</p>
                  <p
                    class="rounded-lg bg-muted/60 px-3 py-2 text-center font-mono text-xs break-all text-muted-foreground"
                  >
                    {{ addr }}
                  </p>
                </div>
              }
            </div>
          </div>

          <div hlmCard>
            <div hlmCardHeader>
              <h2 hlmCardTitle>Personal details</h2>
              <p hlmCardDescription>
                Update how your name and phone number appear across MentorHub.
              </p>
            </div>
            <div hlmCardContent>
              <app-profile-form
                [values]="current"
                [showEmail]="false"
                [save]="save"
                (saved)="onSaved()"
              />
            </div>
          </div>
        </div>
      }
    </main>
  `,
})
export class MyProfile {
  private readonly api = inject(Api);
  private readonly currentUser = inject(CurrentUserService);
  private readonly toast = inject(ToastService);

  protected readonly user = this.currentUser.user;
  protected readonly values = computed<ProfileValues | null>(() => this.currentUser.user());
  protected readonly email = computed(() => this.currentUser.user()?.email ?? '');
  protected readonly fullName = computed(() => {
    const user = this.currentUser.user();
    return user ? displayName(user) : '';
  });

  protected readonly save = async (body: ProfileUpdate): Promise<ProfileValues> => {
    const me = await this.api.invoke(updateMyProfile, { body });
    this.currentUser.me.set(me);
    return me.user ?? { email: '', ...body };
  };

  protected onSaved(): void {
    this.toast.show('Profile saved', { duration: 5000, politeness: 'polite' });
  }
}
