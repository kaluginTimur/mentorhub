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
    .visually-hidden {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip-path: inset(50%);
      white-space: nowrap;
    }
  `,
})
export class Loading {}
