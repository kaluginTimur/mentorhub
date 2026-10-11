import { Component, inject, resource } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterOutlet } from '@angular/router';
import { Api } from './core/api/api';
import { getSystemInfo } from './core/api/fn/system/get-system-info';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, MatToolbarModule, MatCardModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly api = inject(Api);

  protected readonly systemInfo = resource({
    loader: () => this.api.invoke(getSystemInfo),
  });
}
