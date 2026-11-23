import {
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideEllipsisVertical } from '@ng-icons/lucide';

export interface ActionMenuItem {
  key: string;
  label: string;
  testId?: string;
  destructive?: boolean;
}

/**
 * An icon button that opens a small menu of actions. The menu is a plain list under the button, hidden
 * until opened, so it is never clipped by an overlay layer and its items stay in the page's own tree.
 */
@Component({
  selector: 'app-action-menu',
  imports: [NgIcon],
  providers: [provideIcons({ lucideEllipsisVertical })],
  host: {
    class: 'relative inline-block',
    '(document:click)': 'onDocumentClick($event)',
    '(focusout)': 'onFocusOut($event)',
    '(keydown)': 'onKeydown($event)',
  },
  template: `
    <button
      #trigger
      type="button"
      aria-haspopup="menu"
      class="relative inline-flex size-11 items-center justify-center rounded-md text-muted-foreground transition-colors outline-none hover:bg-muted hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 aria-expanded:bg-muted aria-expanded:text-foreground md:size-8"
      [attr.aria-label]="label()"
      [attr.aria-expanded]="open()"
      [attr.data-testid]="testId() ?? null"
      (click)="toggle()"
    >
      <ng-icon name="lucideEllipsisVertical" size="18" aria-hidden="true" />
    </button>
    <div
      role="menu"
      class="absolute end-0 top-full z-30 mt-1 min-w-48 rounded-md border border-border bg-card p-1 text-card-foreground shadow-md"
      [attr.aria-label]="label()"
      [hidden]="!open()"
    >
      @for (item of items(); track item.key) {
        <button
          type="button"
          role="menuitem"
          tabindex="-1"
          class="flex min-h-11 w-full items-center rounded-sm px-2.5 py-1.5 text-start text-sm outline-none hover:bg-muted focus-visible:bg-muted focus-visible:ring-2 focus-visible:ring-ring md:min-h-8"
          [class.text-destructive]="item.destructive"
          [attr.data-testid]="item.testId ?? null"
          (click)="choose(item.key)"
        >
          {{ item.label }}
        </button>
      }
    </div>
  `,
})
export class ActionMenu {
  /** The button's accessible name. */
  readonly label = input.required<string>();
  readonly items = input.required<ActionMenuItem[]>();
  readonly testId = input<string>();
  readonly chosen = output<string>();

  protected readonly open = signal(false);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected toggle(): void {
    if (this.open()) {
      this.close(false);
      return;
    }
    this.open.set(true);
    afterNextRender(() => this.itemButtons()[0]?.focus(), { injector: this.injector });
  }

  protected choose(key: string): void {
    this.close(true);
    this.chosen.emit(key);
  }

  protected onDocumentClick(event: Event): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  protected onFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (this.open() && next && !this.host.nativeElement.contains(next)) {
      this.open.set(false);
    }
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (!this.open()) {
      return;
    }
    const buttons = this.itemButtons();
    const at = buttons.indexOf(document.activeElement as HTMLElement);
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      this.close(true);
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      buttons[(at + 1) % buttons.length]?.focus();
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      buttons[(at - 1 + buttons.length) % buttons.length]?.focus();
    } else if (event.key === 'Tab') {
      this.open.set(false);
    }
  }

  private close(refocus: boolean): void {
    this.open.set(false);
    if (refocus) {
      this.host.nativeElement.querySelector<HTMLElement>('button[aria-haspopup]')?.focus();
    }
  }

  private itemButtons(): HTMLElement[] {
    return [...this.host.nativeElement.querySelectorAll<HTMLElement>('[role=menuitem]')];
  }
}
