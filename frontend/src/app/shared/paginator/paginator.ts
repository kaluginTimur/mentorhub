import { booleanAttribute, Component, computed, input, output } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideChevronLeft, lucideChevronRight } from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';

@Component({
  selector: 'app-paginator',
  imports: [HlmButton, NgIcon],
  providers: [provideIcons({ lucideChevronLeft, lucideChevronRight })],
  template: `
    <nav
      aria-label="Pagination"
      class="flex flex-wrap items-center justify-between gap-3 text-sm text-muted-foreground"
      [class.border-t]="!borderless()"
      [class.border-border]="!borderless()"
      [class.px-4]="!borderless()"
      [class.py-3]="!borderless()"
    >
      <span>{{ summary() }}</span>
      <div class="flex items-center gap-1.5">
        <button
          hlmBtn
          variant="outline"
          size="sm"
          type="button"
          aria-label="Previous page"
          [disabled]="pageIndex() <= 0"
          (click)="pageChange.emit(pageIndex() - 1)"
        >
          <ng-icon name="lucideChevronLeft" aria-hidden="true" />
          Previous
        </button>
        <button
          hlmBtn
          variant="outline"
          size="sm"
          type="button"
          aria-label="Next page"
          [disabled]="!hasNext()"
          (click)="pageChange.emit(pageIndex() + 1)"
        >
          Next
          <ng-icon name="lucideChevronRight" aria-hidden="true" />
        </button>
      </div>
    </nav>
  `,
})
export class Paginator {
  readonly totalItems = input.required<number>();
  readonly pageSize = input.required<number>();
  readonly pageIndex = input.required<number>();
  /** No top border and side padding, for a list in a card that has its own spacing. */
  readonly borderless = input(false, { transform: booleanAttribute });
  readonly pageChange = output<number>();

  protected readonly hasNext = computed(
    () => (this.pageIndex() + 1) * this.pageSize() < this.totalItems(),
  );

  protected readonly summary = computed(() => {
    const total = this.totalItems();
    const size = this.pageSize();
    const index = this.pageIndex();
    const start = index * size + 1;
    if (start > total) {
      return `Page ${index + 1} · ${total} total`;
    }
    const end = Math.min(total, (index + 1) * size);
    return `${start}–${end} of ${total}`;
  });
}
