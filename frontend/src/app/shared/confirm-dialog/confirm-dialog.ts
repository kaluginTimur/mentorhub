import { Component, inject, signal } from '@angular/core';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCheckbox } from '@spartan-ng/helm/checkbox';
import {
  HlmDialogDescription,
  HlmDialogFooter,
  HlmDialogHeader,
  HlmDialogTitle,
} from '@spartan-ng/helm/dialog';
import { HlmLabel } from '@spartan-ng/helm/label';

export interface ConfirmDialogData {
  title: string;
  text: string;
  confirmLabel: string;
  /** An optional checkbox below the text; the dialog then closes with `{ checked }` instead of `true`. */
  checkbox?: { label: string; checked: boolean };
}

export interface ConfirmResult {
  checked: boolean;
}

@Component({
  selector: 'app-confirm-dialog',
  imports: [
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogDescription,
    HlmDialogFooter,
    HlmButton,
    HlmCheckbox,
    HlmLabel,
  ],
  template: `
    <div class="flex flex-col gap-5">
      <div hlmDialogHeader>
        <h2 hlmDialogTitle class="font-display text-lg font-semibold tracking-tight">
          {{ data.title }}
        </h2>
        <p hlmDialogDescription>{{ data.text }}</p>
      </div>
      @if (data.checkbox; as box) {
        <label
          hlmLabel
          for="confirm-dialog-checkbox"
          class="flex cursor-pointer items-center gap-2.5 text-sm font-normal"
        >
          <hlm-checkbox
            inputId="confirm-dialog-checkbox"
            [checked]="checked()"
            (checkedChange)="checked.set($event)"
          />
          <span>{{ box.label }}</span>
        </label>
      }
      <div hlmDialogFooter>
        <button hlmBtn variant="outline" type="button" (click)="ref.close()">Cancel</button>
        <button hlmBtn type="button" (click)="ref.close(result())">
          {{ data.confirmLabel }}
        </button>
      </div>
    </div>
  `,
})
export class ConfirmDialog {
  protected readonly data = injectBrnDialogContext<ConfirmDialogData>();
  protected readonly ref = inject<BrnDialogRef<true | ConfirmResult>>(BrnDialogRef);
  protected readonly checked = signal(this.data.checkbox?.checked ?? false);

  protected result(): true | ConfirmResult {
    return this.data.checkbox ? { checked: this.checked() } : true;
  }
}
