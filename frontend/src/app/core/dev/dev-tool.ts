import { InjectionToken, Type } from '@angular/core';

/** A development-only component the root App renders on every screen; absent from production. */
export const DEV_TOOL = new InjectionToken<Type<unknown>>('DEV_TOOL');
