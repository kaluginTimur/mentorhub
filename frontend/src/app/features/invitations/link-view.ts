import { DatePipe } from '@angular/common';
import {
  Component,
  ElementRef,
  afterNextRender,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CreatedInvitation } from '../../core/api/models/created-invitation';

/** The content of a dialog that shows a new invitation link once. */
@Component({
  selector: 'app-link-view',
  imports: [DatePipe, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Invitation link</h2>
    <mat-dialog-content>
      <p>
        Send this link to {{ created().invitation.email }}. It is shown only once and expires on
        {{ created().invitation.expiresAt | date: 'd MMM y' }}.
      </p>
      <div class="row">
        <mat-form-field class="field">
          <mat-label>Invitation link</mat-label>
          <input #field matInput readonly [value]="created().link" (focus)="field.select()" />
        </mat-form-field>
        <button
          matButton="outlined"
          type="button"
          aria-label="Copy invitation link"
          (click)="copy()"
        >
          Copy link
        </button>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton="filled" type="button" (click)="done.emit()">Done</button>
    </mat-dialog-actions>
  `,
  styles: `
    .row {
      display: flex;
      gap: 16px;
      align-items: flex-start;
    }
    .field {
      flex: 1;
    }
    .row button {
      margin-top: 8px;
    }
  `,
})
export class LinkView {
  private readonly snackBar = inject(MatSnackBar);
  private readonly field = viewChild.required<ElementRef<HTMLInputElement>>('field');

  constructor() {
    afterNextRender(() => this.field().nativeElement.focus());
  }

  readonly created = input.required<CreatedInvitation>();
  readonly done = output();

  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.created().link);
      this.snackBar.open('Link copied', 'Dismiss', { duration: 5000, politeness: 'polite' });
    } catch {
      // no clipboard (insecure origin or permission refused): select the text to copy it by hand
      const input = this.field().nativeElement;
      input.focus();
      input.select();
    }
  }
}
