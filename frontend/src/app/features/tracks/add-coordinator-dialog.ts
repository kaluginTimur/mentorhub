import { Component, computed, inject, signal } from '@angular/core';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import {
  HlmDialogDescription,
  HlmDialogFooter,
  HlmDialogHeader,
  HlmDialogTitle,
} from '@spartan-ng/helm/dialog';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSelectImports } from '@spartan-ng/helm/select';
import { CoordinatorCandidate } from '../../core/api/models/coordinator-candidate';

export interface AddCoordinatorDialogData {
  trackName: string;
  /** Resolves null when the track refused and the page explained it; the dialog then closes. */
  load: () => Promise<CoordinatorCandidate[] | null>;
  /** Names the user; resolves true when the dialog may close (done, or refused and explained). */
  add: (userId: string) => Promise<boolean>;
}

@Component({
  selector: 'app-add-coordinator-dialog',
  imports: [
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogDescription,
    HlmDialogFooter,
    HlmButton,
    HlmLabel,
    HlmSelectImports,
  ],
  template: `
    <div class="flex flex-col gap-5">
      <div hlmDialogHeader>
        <h2
          hlmDialogTitle
          class="pe-10 font-display text-lg font-semibold tracking-tight break-words"
        >
          Add a coordinator to {{ data.trackName }}
        </h2>
        <p hlmDialogDescription>Only active users with the coordinator role can be named.</p>
      </div>

      <form novalidate (submit)="onSubmit($event)" class="space-y-4" [attr.aria-busy]="loading()">
        @if (loading()) {
          <div
            role="progressbar"
            aria-label="Loading coordinators"
            class="h-1 w-full overflow-hidden rounded-full bg-primary/15"
          >
            <div class="animate-mh-indeterminate h-full w-1/3 rounded-full bg-primary"></div>
          </div>
        } @else if (failed()) {
          <div class="flex flex-wrap items-center gap-3">
            <p role="alert" class="text-sm text-destructive">Could not load the coordinators.</p>
            <button hlmBtn variant="outline" size="sm" type="button" (click)="load()">Retry</button>
          </div>
        } @else if (candidates().length === 0) {
          <p class="text-sm text-muted-foreground">
            No active coordinators are available to add. Give the coordinator role to a user first.
          </p>
        } @else {
          <div class="flex flex-col gap-2.5">
            <label hlmLabel for="add-coordinator">Coordinator</label>
            <hlm-select
              [value]="selected()"
              [itemToString]="candidateLabel"
              (valueChange)="select($event)"
            >
              <hlm-select-trigger
                buttonId="add-coordinator"
                class="w-full"
                [forceInvalid]="missing()"
                [attr.aria-invalid]="missing() ? 'true' : null"
                [attr.aria-describedby]="missing() ? 'add-coordinator-error' : null"
              >
                <hlm-select-value placeholder="Select a coordinator" />
              </hlm-select-trigger>
              <hlm-select-content *hlmSelectPortal>
                @for (candidate of candidates(); track candidate.id) {
                  <hlm-select-item [value]="candidate.id">
                    {{ candidate.name }} ({{ candidate.email }})
                  </hlm-select-item>
                }
              </hlm-select-content>
            </hlm-select>
            @if (missing()) {
              <p id="add-coordinator-error" class="text-xs text-destructive" role="alert">
                Choose a coordinator
              </p>
            }
          </div>
        }

        <div hlmDialogFooter>
          <button hlmBtn variant="outline" type="button" (click)="ref.close()">Cancel</button>
          <button hlmBtn type="submit" [disabled]="saving() || loading() || failed()">Add</button>
        </div>
      </form>
    </div>
  `,
})
export class AddCoordinatorDialog {
  protected readonly data = injectBrnDialogContext<AddCoordinatorDialogData>();
  protected readonly ref = inject<BrnDialogRef<boolean>>(BrnDialogRef);

  protected readonly candidates = signal<CoordinatorCandidate[]>([]);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly saving = signal(false);
  protected readonly selected = signal('');
  private readonly submitted = signal(false);
  protected readonly missing = computed(() => this.submitted() && this.selected() === '');

  protected readonly candidateLabel = (id: string): string => {
    const candidate = this.candidates().find((c) => c.id === id);
    return candidate ? `${candidate.name} (${candidate.email})` : '';
  };

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.failed.set(false);
    try {
      const candidates = await this.data.load();
      if (candidates === null) {
        this.ref.close();
        return;
      }
      this.candidates.set(candidates);
    } catch {
      this.failed.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  protected select(value: string | null | undefined): void {
    this.selected.set(value ?? '');
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.saving() || this.loading() || this.failed()) {
      return;
    }
    this.submitted.set(true);
    if (this.selected() === '') {
      return;
    }
    this.saving.set(true);
    try {
      if (await this.data.add(this.selected())) {
        this.ref.close(true);
      }
    } finally {
      this.saving.set(false);
    }
  }
}
