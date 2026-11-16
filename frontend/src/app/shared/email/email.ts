import { Component, computed, input } from '@angular/core';

/**
 * An email address that always wraps, never truncates: it breaks after "@" and "." first, and
 * anywhere only when a piece is still too wide. The break hints are `<wbr>`, so copying the text
 * gives the address unchanged.
 */
@Component({
  selector: 'app-email',
  host: { class: 'break-words' },
  // prettier-ignore
  template: `@for (part of parts(); track $index) {{{ part }}<wbr />}`,
})
export class Email {
  readonly value = input.required<string>();
  protected readonly parts = computed(() => this.value().split(/(?<=[@.])/));
}
