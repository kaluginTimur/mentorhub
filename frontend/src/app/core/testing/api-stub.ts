import { HttpResponse } from '@angular/common/http';
import { Provider } from '@angular/core';
import { Api, ApiFnOptional, ApiFnRequired } from '../api/api';
import { StrictHttpResponse } from '../api/strict-http-response';

type AnyApiFn = (...args: never[]) => unknown;
type StoredHandler = (params: unknown) => unknown;

interface Entry {
  defaultHandler?: StoredHandler;
  onceQueue: StoredHandler[];
}

export interface ApiCall {
  readonly fn: AnyApiFn;
  readonly params: unknown;
}

export interface ApiStub {
  resolve<P, R>(fn: ApiFnOptional<P, R>, value: R): ApiStub;
  resolve<P, R>(fn: ApiFnRequired<P, R>, value: R): ApiStub;

  resolveOnce<P, R>(fn: ApiFnOptional<P, R>, value: R): ApiStub;
  resolveOnce<P, R>(fn: ApiFnRequired<P, R>, value: R): ApiStub;

  handle<P, R>(
    fn: ApiFnOptional<P, R>,
    handler: (params: P | undefined) => R | Promise<R>,
  ): ApiStub;
  handle<P, R>(fn: ApiFnRequired<P, R>, handler: (params: P) => R | Promise<R>): ApiStub;

  handleOnce<P, R>(
    fn: ApiFnOptional<P, R>,
    handler: (params: P | undefined) => R | Promise<R>,
  ): ApiStub;
  handleOnce<P, R>(fn: ApiFnRequired<P, R>, handler: (params: P) => R | Promise<R>): ApiStub;

  reject<P, R>(fn: ApiFnRequired<P, R> | ApiFnOptional<P, R>, error: unknown): ApiStub;
  rejectOnce<P, R>(fn: ApiFnRequired<P, R> | ApiFnOptional<P, R>, error: unknown): ApiStub;

  callsFor<P, R>(fn: ApiFnOptional<P, R>): readonly (P | undefined)[];
  callsFor<P, R>(fn: ApiFnRequired<P, R>): readonly P[];

  allCalls(): readonly ApiCall[];
  reset(): void;
}

type ApiTarget = Pick<Api, 'invoke' | 'invoke$Response' | 'rootUrl'>;

const stubTargets = new WeakMap<ApiStub, ApiTarget>();

function fnLabel(fn: AnyApiFn): string {
  const path = (fn as { PATH?: string }).PATH;
  return path ?? (fn.name || 'unknown');
}

class ApiStubImpl implements ApiStub {
  private readonly entries = new Map<AnyApiFn, Entry>();
  private readonly calls: ApiCall[] = [];
  private rootUrl = '';

  constructor() {
    stubTargets.set(this, {
      get rootUrl(): string {
        return this.rootUrlValue;
      },
      set rootUrl(value: string) {
        this.rootUrlValue = value;
      },
      invoke: this.invoke.bind(this) as ApiTarget['invoke'],
      invoke$Response: this.invoke$Response.bind(this) as ApiTarget['invoke$Response'],
    } as ApiTarget & { rootUrlValue: string });
  }

  private get rootUrlValue(): string {
    return this.rootUrl;
  }

  private set rootUrlValue(value: string) {
    this.rootUrl = value;
  }

  private entryFor(fn: AnyApiFn): Entry {
    let entry = this.entries.get(fn);
    if (!entry) {
      entry = { onceQueue: [] };
      this.entries.set(fn, entry);
    }
    return entry;
  }

  private async invoke<P, R>(
    fn: ApiFnRequired<P, R> | ApiFnOptional<P, R>,
    params?: P,
  ): Promise<R> {
    this.calls.push({ fn, params });
    const entry = this.entries.get(fn);
    const handler = entry?.onceQueue.shift() ?? entry?.defaultHandler;
    if (!handler) {
      throw new Error(`Unexpected API call: ${fnLabel(fn)}`);
    }
    return (await handler(params)) as R;
  }

  private async invoke$Response<P, R>(
    fn: ApiFnRequired<P, R> | ApiFnOptional<P, R>,
    params?: P,
  ): Promise<StrictHttpResponse<R>> {
    const body = await this.invoke(fn as ApiFnOptional<P, R>, params);
    return new HttpResponse({ body }) as StrictHttpResponse<R>;
  }

  resolve<P, R>(fn: ApiFnOptional<P, R>, value: R): ApiStub;
  resolve<P, R>(fn: ApiFnRequired<P, R>, value: R): ApiStub;
  resolve<P, R>(fn: ApiFnOptional<P, R> | ApiFnRequired<P, R>, value: R): ApiStub {
    this.entryFor(fn).defaultHandler = () => value;
    return this;
  }

  resolveOnce<P, R>(fn: ApiFnOptional<P, R>, value: R): ApiStub;
  resolveOnce<P, R>(fn: ApiFnRequired<P, R>, value: R): ApiStub;
  resolveOnce<P, R>(fn: ApiFnOptional<P, R> | ApiFnRequired<P, R>, value: R): ApiStub {
    this.entryFor(fn).onceQueue.push(() => value);
    return this;
  }

  handle<P, R>(
    fn: ApiFnOptional<P, R>,
    handler: (params: P | undefined) => R | Promise<R>,
  ): ApiStub;
  handle<P, R>(fn: ApiFnRequired<P, R>, handler: (params: P) => R | Promise<R>): ApiStub;
  handle<P, R>(
    fn: ApiFnOptional<P, R> | ApiFnRequired<P, R>,
    handler: ((params: P | undefined) => R | Promise<R>) | ((params: P) => R | Promise<R>),
  ): ApiStub {
    this.entryFor(fn).defaultHandler = (params: unknown) =>
      (handler as (params: P | undefined) => R | Promise<R>)(params as P | undefined);
    return this;
  }

  handleOnce<P, R>(
    fn: ApiFnOptional<P, R>,
    handler: (params: P | undefined) => R | Promise<R>,
  ): ApiStub;
  handleOnce<P, R>(fn: ApiFnRequired<P, R>, handler: (params: P) => R | Promise<R>): ApiStub;
  handleOnce<P, R>(
    fn: ApiFnOptional<P, R> | ApiFnRequired<P, R>,
    handler: ((params: P | undefined) => R | Promise<R>) | ((params: P) => R | Promise<R>),
  ): ApiStub {
    this.entryFor(fn).onceQueue.push((params: unknown) =>
      (handler as (params: P | undefined) => R | Promise<R>)(params as P | undefined),
    );
    return this;
  }

  reject<P, R>(fn: ApiFnRequired<P, R> | ApiFnOptional<P, R>, error: unknown): ApiStub {
    this.entryFor(fn).defaultHandler = () => {
      throw error;
    };
    return this;
  }

  rejectOnce<P, R>(fn: ApiFnRequired<P, R> | ApiFnOptional<P, R>, error: unknown): ApiStub {
    this.entryFor(fn).onceQueue.push(() => {
      throw error;
    });
    return this;
  }

  callsFor<P, R>(fn: ApiFnOptional<P, R>): readonly (P | undefined)[];
  callsFor<P, R>(fn: ApiFnRequired<P, R>): readonly P[];
  callsFor<P, R>(fn: ApiFnOptional<P, R> | ApiFnRequired<P, R>): readonly (P | undefined)[] {
    return this.calls.filter((c) => c.fn === fn).map((c) => c.params as P | undefined);
  }

  allCalls(): readonly ApiCall[] {
    return [...this.calls];
  }

  reset(): void {
    this.entries.clear();
    this.calls.length = 0;
  }
}

export function createApiStub(): ApiStub {
  return new ApiStubImpl();
}

export function provideApiStub(stub: ApiStub): Provider {
  const target = stubTargets.get(stub);
  if (!target) {
    throw new Error('provideApiStub requires an ApiStub created by createApiStub()');
  }
  return { provide: Api, useValue: target };
}
