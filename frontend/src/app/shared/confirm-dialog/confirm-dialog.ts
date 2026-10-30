import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

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
  imports: [MatDialogModule, MatButtonModule, MatCheckboxModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p class="text">{{ data.text }}</p>
      @if (data.checkbox; as box) {
        <mat-checkbox [checked]="checked()" (change)="checked.set($event.checked)">
          {{ box.label }}
        </mat-checkbox>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton type="button" (click)="ref.close()">Cancel</button>
      <button matButton="filled" type="button" (click)="ref.close(result())">
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .text {
      margin: 0 0 8px;
    }
  `,
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  protected readonly ref = inject<MatDialogRef<ConfirmDialog>>(MatDialogRef);
  protected readonly checked = signal(this.data.checkbox?.checked ?? false);

  protected result(): true | ConfirmResult {
    return this.data.checkbox ? { checked: this.checked() } : true;
  }
}
