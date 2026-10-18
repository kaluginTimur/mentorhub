import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AccessChannel } from './access-channel';

describe('AccessChannel', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('delivers a change from one instance to another one on the same channel', async () => {
    const sender = TestBed.inject(AccessChannel);
    const receiver = Injector.create({
      providers: [{ provide: AccessChannel, useClass: AccessChannel }],
      parent: TestBed.inject(Injector),
    }).get(AccessChannel);
    const handler = vi.fn();
    const off = receiver.onChanged(handler);
    sender.notifyChanged();
    await vi.waitFor(() => expect(handler).toHaveBeenCalledOnce());
    off();
    sender.notifyChanged();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(handler).toHaveBeenCalledOnce();
  });

  it('closes the channel when its injector is destroyed', () => {
    const channel = TestBed.inject(AccessChannel);
    TestBed.resetTestingModule();
    expect(() => channel.notifyChanged()).toThrow();
  });

  it('does nothing without BroadcastChannel', () => {
    vi.stubGlobal('BroadcastChannel', undefined);
    const channel = TestBed.inject(AccessChannel);
    expect(() => channel.notifyChanged()).not.toThrow();
    expect(() => channel.onChanged(() => undefined)()).not.toThrow();
  });
});
