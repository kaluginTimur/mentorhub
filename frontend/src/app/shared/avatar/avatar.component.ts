import { Component, computed, input } from '@angular/core';
import { avatarColor, initials } from './avatar';

/**
 * A round avatar with the user's initials on a color derived from the id.
 * Decorative: put it next to the name, which carries the meaning.
 */
@Component({
  selector: 'app-avatar',
  template: '{{ text() }}',
  host: {
    class: 'avatar',
    'aria-hidden': 'true',
    '[style.--avatar-color]': 'color()',
    '[style.--avatar-size.px]': 'size()',
  },
  styles: `
    :host {
      display: inline-grid;
      place-items: center;
      flex: none;
      box-sizing: border-box;
      inline-size: var(--avatar-size);
      block-size: var(--avatar-size);
      overflow: hidden;
      border-radius: 50%;
      background: var(--avatar-color);
      color: var(--app-avatar-on-color);
      font-family: var(--mat-sys-label-large-font);
      font-weight: 600;
      font-size: calc(var(--avatar-size) * 0.4);
      line-height: 1;
      letter-spacing: 0.02em;
      white-space: nowrap;
      user-select: none;
    }
  `,
})
export class Avatar {
  readonly user = input.required<{
    id: string;
    firstName?: string | null;
    lastName?: string | null;
    email: string;
  }>();
  /** The diameter in pixels. */
  readonly size = input(32);

  protected readonly color = computed(() => avatarColor(this.user().id));
  protected readonly text = computed(() => initials(this.user()));
}
