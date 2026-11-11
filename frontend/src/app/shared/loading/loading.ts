import { Component } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-loading',
  imports: [MatProgressSpinnerModule],
  template: `
    <main class="loading">
      <div role="status">
        <mat-spinner diameter="40" aria-hidden="true" />
        <span class="visually-hidden">Loading</span>
      </div>
    </main>
  `,
  styles: `
    .loading {
      display: flex;
      justify-content: center;
      padding: 48px 16px;
    }
  `,
})
export class Loading {}
