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
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCopy } from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogFooter, HlmDialogHeader, HlmDialogTitle } from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { InvitationLink } from '../../core/api/models/invitation-link';
import { ToastService } from '../../shared/toast/toast.service';

/** The content of a dialog that shows an invitation link: 'new' offers to copy it, 'copy' is the manual fallback. */
@Component({
  selector: 'app-link-view',
  imports: [
    DatePipe,
    NgIcon,
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogFooter,
    HlmButton,
    HlmInput,
    HlmLabel,
  ],
  providers: [provideIcons({ lucideCopy })],
  template: `
    <div class="flex flex-col gap-5">
      <div hlmDialogHeader>
        <h2 hlmDialogTitle class="font-display text-lg font-semibold tracking-tight">
          Invitation link
        </h2>
      </div>

      <div class="space-y-4">
        @if (mode() === 'new' && emailRequested()) {
          <p class="text-sm text-muted-foreground">
            An email with this link is on its way to {{ link().email }}. It expires on
            {{ link().expiresAt | date: 'd MMM y' }}. You can also copy the link, and copy it again
            from the invitations list while it is pending.
          </p>
        } @else if (mode() === 'new') {
          <p class="text-sm text-muted-foreground">
            Send this link to {{ link().email }}. It expires on
            {{ link().expiresAt | date: 'd MMM y' }}. You can copy it again from the invitations
            list while it is pending.
          </p>
        } @else {
          <p class="text-sm text-muted-foreground">
            Copy this link and send it to {{ link().email }}. It expires on
            {{ link().expiresAt | date: 'd MMM y' }}.
          </p>
        }

        <div class="flex flex-col gap-2.5">
          <label hlmLabel for="invitation-link-field">Invitation link</label>
          <div class="flex items-center gap-2">
            <input
              #field
              hlmInput
              id="invitation-link-field"
              readonly
              class="font-mono text-xs"
              [value]="link().link"
              (focus)="field.select()"
            />
            @if (mode() === 'new') {
              <button
                hlmBtn
                variant="outline"
                type="button"
                aria-label="Copy invitation link"
                (click)="copy()"
              >
                <ng-icon name="lucideCopy" aria-hidden="true" />
                Copy link
              </button>
            }
          </div>
        </div>
      </div>

      <div hlmDialogFooter>
        <button hlmBtn type="button" (click)="done.emit()">Done</button>
      </div>
    </div>
  `,
})
export class LinkView {
  private readonly toast = inject(ToastService);
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
  /** The invitation email was requested together with this link. */
  readonly emailRequested = input(false);
  readonly done = output();

  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.link().link);
      this.toast.show('Link copied', { duration: 5000, politeness: 'polite' });
    } catch {
      // no clipboard (insecure origin or permission refused): select the text to copy it by hand
      const input = this.field().nativeElement;
      input.focus();
      input.select();
    }
  }
}
