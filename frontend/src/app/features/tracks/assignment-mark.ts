import { Component, input } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideTriangleAlert } from '@ng-icons/lucide';
import { HlmBadge } from '@spartan-ng/helm/badge';

/** The mark of a coordinator assignment that grants nothing; the icon backs the words, never replaces them. */
@Component({
  selector: 'app-assignment-mark',
  imports: [HlmBadge, NgIcon],
  providers: [provideIcons({ lucideTriangleAlert })],
  template: `
    <span
      hlmBadge
      variant="outline"
      class="border-transparent bg-mh-warning-soft text-mh-warning-soft-foreground"
    >
      <ng-icon name="lucideTriangleAlert" aria-hidden="true" />
      {{ label() }}
    </span>
  `,
})
export class AssignmentMark {
  readonly label = input.required<string>();
}
