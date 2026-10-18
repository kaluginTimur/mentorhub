import { Component, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-unavailable',
  imports: [MatCardModule, MatButtonModule],
  template: `
    <main>
      <mat-card appearance="outlined" role="alert">
        <mat-card-content>
          <h1 tabindex="-1">MentorHub is unavailable. Try again later.</h1>
        </mat-card-content>
        <mat-card-actions align="end">
          <button matButton="filled" type="button" (click)="retry.emit()">Retry</button>
        </mat-card-actions>
      </mat-card>
    </main>
  `,
  styles: `
    main {
      display: flex;
      justify-content: center;
      padding: 48px 16px;
    }
    mat-card {
      max-width: 480px;
      width: 100%;
    }
    h1 {
      font: var(--mat-sys-title-large);
      margin: 16px 0 0;
      outline: none;
    }
  `,
})
export class Unavailable {
  readonly retry = output();
}
