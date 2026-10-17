import { Component, computed, inject, resource } from '@angular/core';
import { Api } from '../../core/api/api';
import { getSystemInfo } from '../../core/api/fn/system/get-system-info';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { greetingName } from '../../core/auth/user-name';

@Component({
  selector: 'app-home',
  template: `
    <main>
      <h1>Welcome, {{ greeting() }}</h1>
    </main>
    <footer>
      @if (systemInfo.hasValue()) {
        @let info = systemInfo.value();
        {{ info.name }} {{ info.version }}
      }
    </footer>
  `,
  styleUrl: './home.scss',
})
export class Home {
  private readonly api = inject(Api);
  private readonly currentUser = inject(CurrentUserService);

  protected readonly systemInfo = resource({ loader: () => this.api.invoke(getSystemInfo) });
  protected readonly greeting = computed(() => {
    const user = this.currentUser.user();
    return user ? greetingName(user) : '';
  });
}
