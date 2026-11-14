import { Component, computed, inject, resource } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideArrowRight, lucideMail, lucideUser, lucideUsers } from '@ng-icons/lucide';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { Api } from '../../core/api/api';
import { getSystemInfo } from '../../core/api/fn/system/get-system-info';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { greetingName } from '../../core/auth/user-name';

@Component({
  selector: 'app-home',
  imports: [RouterLink, HlmCardImports, NgIcon],
  providers: [provideIcons({ lucideUsers, lucideMail, lucideUser, lucideArrowRight })],
  template: `
    <main class="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <section
        class="relative overflow-hidden rounded-2xl bg-gradient-to-br from-mh-hero-start to-mh-hero-end p-6 text-mh-hero-foreground shadow-md sm:p-10"
      >
        <div
          aria-hidden="true"
          class="pointer-events-none absolute -top-20 -right-16 size-72 rounded-full bg-mh-highlight/20 blur-3xl"
        ></div>
        <div class="relative space-y-2">
          <p
            class="inline-flex items-center gap-2 text-xs font-semibold tracking-wider text-mh-topbar-muted uppercase"
          >
            <span aria-hidden="true" class="size-2 rounded-full bg-mh-highlight"></span>
            MentorHub Workspace
          </p>
          <h1 class="font-display text-2xl font-bold tracking-tight sm:text-3xl">
            Welcome, {{ greeting() }}
          </h1>
          <p class="max-w-2xl text-sm text-mh-topbar-muted sm:text-base">
            Manage your profile and mentor workspace tools from one place.
          </p>
        </div>
      </section>

      <section aria-label="Quick actions" class="mt-8">
        <h2 class="text-xs font-semibold tracking-wider text-muted-foreground uppercase">
          Quick actions
        </h2>
        <div class="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          @if (canReadUsers()) {
            <a
              routerLink="/users"
              hlmCard
              class="group transition-all hover:ring-primary/40 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <div hlmCardHeader class="flex-row items-start gap-3">
                <div
                  class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-secondary text-secondary-foreground"
                  aria-hidden="true"
                >
                  <ng-icon name="lucideUsers" class="size-5" />
                </div>
                <div class="min-w-0 flex-1 space-y-1">
                  <div hlmCardTitle class="flex items-center justify-between gap-2">
                    <span>Users</span>
                    <ng-icon
                      name="lucideArrowRight"
                      class="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5"
                      aria-hidden="true"
                    />
                  </div>
                  <p hlmCardDescription>Browse and manage mentor and student accounts.</p>
                </div>
              </div>
            </a>
          }

          @if (canInvite()) {
            <a
              routerLink="/invitations"
              hlmCard
              class="group transition-all hover:ring-primary/40 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <div hlmCardHeader class="flex-row items-start gap-3">
                <div
                  class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-mh-highlight-soft text-mh-highlight-soft-foreground"
                  aria-hidden="true"
                >
                  <ng-icon name="lucideMail" class="size-5" />
                </div>
                <div class="min-w-0 flex-1 space-y-1">
                  <div hlmCardTitle class="flex items-center justify-between gap-2">
                    <span>Invitations</span>
                    <ng-icon
                      name="lucideArrowRight"
                      class="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5"
                      aria-hidden="true"
                    />
                  </div>
                  <p hlmCardDescription>Send invitations and track onboarding status.</p>
                </div>
              </div>
            </a>
          }

          <a
            routerLink="/profile"
            hlmCard
            class="group transition-all hover:ring-primary/40 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <div hlmCardHeader class="flex-row items-start gap-3">
              <div
                class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-secondary text-secondary-foreground"
                aria-hidden="true"
              >
                <ng-icon name="lucideUser" class="size-5" />
              </div>
              <div class="min-w-0 flex-1 space-y-1">
                <div hlmCardTitle class="flex items-center justify-between gap-2">
                  <span>Profile</span>
                  <ng-icon
                    name="lucideArrowRight"
                    class="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5"
                    aria-hidden="true"
                  />
                </div>
                <p hlmCardDescription>Review your account details and update your name.</p>
              </div>
            </div>
          </a>
        </div>
      </section>
    </main>
    <footer class="mx-auto max-w-6xl px-4 pb-8 text-xs text-muted-foreground sm:px-6">
      @if (systemInfo.hasValue()) {
        @let info = systemInfo.value();
        <span class="font-mono">{{ info.name }} {{ info.version }}</span>
      }
    </footer>
  `,
})
export class Home {
  private readonly api = inject(Api);
  private readonly currentUser = inject(CurrentUserService);

  protected readonly systemInfo = resource({ loader: () => this.api.invoke(getSystemInfo) });
  protected readonly canReadUsers = computed(() => this.currentUser.hasPermission('USERS_READ'));
  protected readonly canInvite = computed(() => this.currentUser.hasPermission('USERS_INVITE'));
  protected readonly greeting = computed(() => {
    const user = this.currentUser.user();
    return user ? greetingName(user) : '';
  });
}
