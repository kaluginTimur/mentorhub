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
import { InvitationLink } from '../../core/api/models/invitation-link';

/** The content of a dialog that shows an invitation link: 'new' offers to copy it, 'copy' is the manual fallback. */
@Component({
  selector: 'app-link-view',
  imports: [DatePipe, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Invitation link</h2>
    <mat-dialog-content>
      @if (mode() === 'new') {
        <p>
          Send this link to {{ link().email }}. It expires on
          {{ link().expiresAt | date: 'd MMM y' }}. You can copy it again from the invitations list
          while it is pending.
        </p>
      } @else {
        <p>
          Copy this link and send it to {{ link().email }}. It expires on
          {{ link().expiresAt | date: 'd MMM y' }}.
        </p>
      }
      <div class="row">
        <mat-form-field class="field">
          <mat-label>Invitation link</mat-label>
          <input #field matInput readonly [value]="link().link" (focus)="field.select()" />
        </mat-form-field>
        @if (mode() === 'new') {
          <button
            matButton="outlined"
            type="button"
            aria-label="Copy invitation link"
            (click)="copy()"
          >
            Copy link
          </button>
        }
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
    afterNextRender(() => {
      const input = this.field().nativeElement;
      input.focus();
      input.select();
    });
  }

  readonly link = input.required<InvitationLink>();
  readonly mode = input<'new' | 'copy'>('new');
  readonly done = output();

  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.link().link);
      this.snackBar.open('Link copied', 'Dismiss', { duration: 5000, politeness: 'polite' });
    } catch {
      // no clipboard (insecure origin or permission refused): select the text to copy it by hand
      const input = this.field().nativeElement;
      input.focus();
      input.select();
    }
  }
}
