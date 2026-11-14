import { Component, output } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideRefreshCw, lucideTriangleAlert } from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';

@Component({
  selector: 'app-unavailable',
  imports: [HlmCardImports, HlmButton, NgIcon],
  providers: [provideIcons({ lucideTriangleAlert, lucideRefreshCw })],
  template: `
    <main class="flex min-h-[60vh] items-center justify-center px-4 py-12">
      <div hlmCard role="alert" class="w-full max-w-md">
        <div hlmCardContent class="flex flex-col gap-4 pt-2">
          <div
            class="flex size-10 items-center justify-center rounded-lg bg-destructive/10 text-destructive"
            aria-hidden="true"
          >
            <ng-icon name="lucideTriangleAlert" class="size-5" />
          </div>
          <div class="space-y-1.5">
            <h1 tabindex="-1" class="text-lg font-semibold tracking-tight outline-none">
              MentorHub is unavailable. Try again later.
            </h1>
            <p class="text-sm text-muted-foreground">
              Please check your connection and try again in a moment.
            </p>
          </div>
        </div>
        <div hlmCardFooter class="justify-end">
          <button hlmBtn type="button" (click)="retry.emit()">
            <ng-icon name="lucideRefreshCw" aria-hidden="true" />
            Retry
          </button>
        </div>
      </div>
    </main>
  `,
})
export class Unavailable {
  readonly retry = output();
}
