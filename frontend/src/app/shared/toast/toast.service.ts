import {
  ApplicationRef,
  Component,
  ComponentRef,
  createComponent,
  DestroyRef,
  DOCUMENT,
  EnvironmentInjector,
  inject,
  Service,
  signal,
} from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCircleAlert } from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';

export type ToastPoliteness = 'polite' | 'assertive';

export interface ToastOptions {
  action?: string;
  duration?: number;
  politeness?: ToastPoliteness;
}

export interface ToastItem {
  id: number;
  message: string;
  action: string;
  politeness: ToastPoliteness;
}

const MAX_VISIBLE_TOASTS = 3;

@Service()
export class ToastService {
  private readonly appRef = inject(ApplicationRef);
  private readonly envInjector = inject(EnvironmentInjector);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  private readonly toastsState = signal<ToastItem[]>([]);
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();
  private hostRef: ComponentRef<ToastHost> | null = null;
  private nextId = 1;

  readonly toasts = this.toastsState.asReadonly();

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.clearTimers();
      if (this.hostRef) {
        this.appRef.detachView(this.hostRef.hostView);
        this.hostRef.destroy();
        this.hostRef = null;
      }
    });
  }

  show(message: string, options?: ToastOptions): number {
    this.ensureHost();

    const id = this.nextId++;
    const item: ToastItem = {
      id,
      message,
      action: options?.action ?? 'Dismiss',
      politeness: options?.politeness ?? 'polite',
    };

    this.toastsState.update((current) => {
      const next = [...current, item];
      if (next.length <= MAX_VISIBLE_TOASTS) {
        return next;
      }
      const evicted = next.slice(0, next.length - MAX_VISIBLE_TOASTS);
      for (const old of evicted) {
        this.clearTimer(old.id);
      }
      return next.slice(-MAX_VISIBLE_TOASTS);
    });

    const duration = options?.duration;
    if (typeof duration === 'number' && Number.isFinite(duration) && duration > 0) {
      this.timers.set(
        id,
        setTimeout(() => this.dismiss(id), duration),
      );
    }

    return id;
  }

  dismiss(id: number): void {
    this.clearTimer(id);
    this.toastsState.update((current) => current.filter((item) => item.id !== id));
  }

  dismissAll(): void {
    this.clearTimers();
    this.toastsState.set([]);
  }

  private ensureHost(): void {
    if (this.hostRef) {
      return;
    }
    const ref = createComponent(ToastHost, {
      environmentInjector: this.envInjector,
    });
    this.appRef.attachView(ref.hostView);
    this.document.body.appendChild(ref.location.nativeElement as HTMLElement);
    this.hostRef = ref;
  }

  private clearTimer(id: number): void {
    const timer = this.timers.get(id);
    if (timer !== undefined) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
  }

  private clearTimers(): void {
    for (const timer of this.timers.values()) {
      clearTimeout(timer);
    }
    this.timers.clear();
  }
}

@Component({
  selector: 'app-toast-host',
  imports: [HlmButton, NgIcon],
  providers: [provideIcons({ lucideCircleAlert })],
  host: {
    class:
      'pointer-events-none fixed inset-x-0 bottom-14 z-50 flex flex-col items-center gap-2 px-4 sm:inset-x-auto sm:right-4 sm:items-end',
  },
  template: `
    @for (toast of toasts(); track toast.id) {
      <div
        class="pointer-events-auto flex w-full max-w-md items-center justify-between gap-3 rounded-lg border bg-popover px-4 py-3 text-sm text-popover-foreground shadow-lg"
        [class.border-destructive/50]="toast.politeness === 'assertive'"
        [class.border-border]="toast.politeness !== 'assertive'"
        [attr.role]="toast.politeness === 'assertive' ? 'alert' : 'status'"
        [attr.aria-live]="toast.politeness"
        aria-atomic="true"
      >
        @if (toast.politeness === 'assertive') {
          <ng-icon
            name="lucideCircleAlert"
            class="size-4 shrink-0 text-destructive"
            aria-hidden="true"
          />
        }
        <span class="flex-1">{{ toast.message }}</span>
        <button hlmBtn variant="ghost" size="sm" type="button" (click)="dismiss(toast.id)">
          {{ toast.action }}
        </button>
      </div>
    }
  `,
})
export class ToastHost {
  private readonly toastService = inject(ToastService);

  protected readonly toasts = this.toastService.toasts;

  protected dismiss(id: number): void {
    this.toastService.dismiss(id);
  }
}
