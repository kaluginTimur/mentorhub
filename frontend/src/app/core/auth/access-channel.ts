import { DestroyRef, Service, inject } from '@angular/core';

const CHANNEL_NAME = 'mentorhub.access';

/** Tells the other tabs of this browser that the caller's access changed. */
@Service()
export class AccessChannel {
  private readonly channel: BroadcastChannel | null =
    typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(CHANNEL_NAME);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.channel?.close());
  }

  notifyChanged(): void {
    this.channel?.postMessage('changed');
  }

  /** Calls the handler when another tab reports a change; returns the unsubscribe function. */
  onChanged(handler: () => void): () => void {
    if (!this.channel) {
      return () => undefined;
    }
    const channel = this.channel;
    channel.addEventListener('message', handler);
    return () => channel.removeEventListener('message', handler);
  }
}
