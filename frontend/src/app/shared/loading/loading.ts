import { Component } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideLoaderCircle } from '@ng-icons/lucide';

@Component({
  selector: 'app-loading',
  imports: [NgIcon],
  providers: [provideIcons({ lucideLoaderCircle })],
  template: `
    <main class="flex min-h-[40vh] items-center justify-center px-4 py-12">
      <div role="status" class="flex items-center justify-center">
        <ng-icon
          name="lucideLoaderCircle"
          class="size-8 animate-spin text-primary"
          aria-hidden="true"
        />
        <span class="sr-only">Loading</span>
      </div>
    </main>
  `,
})
export class Loading {}
