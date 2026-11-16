import { DatePipe } from '@angular/common';
import { Component, computed, inject, resource } from '@angular/core';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { Api } from '../../core/api/api';
import { listMyEnrollments } from '../../core/api/fn/enrollments/list-my-enrollments';
import { DATE_FORMAT } from '../../shared/date/date-format';
import { Loading } from '../../shared/loading/loading';
import { trackLabel } from '../../shared/track-label/track-label';
import { Unavailable } from '../../shared/unavailable/unavailable';
import { Email } from '../../shared/email/email';

/** The caller's own enrollment and the coordinators to contact (ENR-8). */
@Component({
  selector: 'app-my-enrollment-page',
  imports: [Email, DatePipe, HlmCardImports, Loading, Unavailable],
  template: `
    @if (enrollments.error()) {
      <app-unavailable (retry)="enrollments.reload()" />
    } @else if (!enrollments.hasValue()) {
      <app-loading />
    } @else {
      <main class="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-8 sm:px-6">
        <h1 class="font-display text-2xl font-bold tracking-tight">My enrollment</h1>

        @if (enrollment(); as e) {
          <div class="grid gap-6 md:grid-cols-[3fr_7fr]">
            <div hlmCard>
              <div hlmCardHeader>
                <h2 hlmCardTitle class="break-words">{{ label(e.track) }}</h2>
                <p hlmCardDescription>
                  Enrolled from
                  <time [attr.datetime]="e.startDate">{{ e.startDate | date: dateFormat }}</time>
                </p>
              </div>
            </div>

            <div hlmCard>
              <div hlmCardHeader>
                <h2 hlmCardTitle>Coordinators to contact</h2>
              </div>
              <div hlmCardContent>
                @if (e.coordinators.length > 0) {
                  <ul class="divide-y divide-border">
                    @for (c of e.coordinators; track c.email) {
                      <li class="flex flex-col gap-1 py-2.5">
                        <span class="font-medium break-words">{{ c.name }}</span>
                        <a
                          class="w-fit font-mono text-xs text-mh-link hover:text-mh-link-hover hover:underline"
                          [href]="'mailto:' + c.email"
                          ><app-email [value]="c.email"
                        /></a>
                        @if (c.phone) {
                          <span class="font-mono text-xs text-muted-foreground">{{ c.phone }}</span>
                        }
                      </li>
                    }
                  </ul>
                } @else {
                  <p class="text-sm text-muted-foreground">
                    This track has no coordinator to contact
                  </p>
                }
              </div>
            </div>
          </div>
        } @else {
          <div
            class="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border bg-card px-4 py-12 text-center text-sm text-muted-foreground"
          >
            <p>You are not enrolled yet</p>
          </div>
        }
      </main>
    }
  `,
})
export class MyEnrollmentPage {
  protected readonly dateFormat = DATE_FORMAT;
  private readonly api = inject(Api);

  protected readonly enrollments = resource({
    loader: () => this.api.invoke(listMyEnrollments),
  });
  /** A user has at most one active enrollment at a time. */
  protected readonly enrollment = computed(() => this.enrollments.value()?.items[0]);
  protected readonly label = trackLabel;
}
