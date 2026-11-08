// Development tools only: the production build replaces this file with dev-tools.prod.ts. A new tool also adds a
// string only it contains to frontend/scripts/check-prod-build.mjs, which looks for nothing else.
import { HttpInterceptorFn } from '@angular/common/http';
import { Provider } from '@angular/core';
import { DEV_TOOL } from '../app/core/dev/dev-tool';
import { failureInterceptor } from '../app/core/dev/failure-interceptor';
import { FailureSwitch } from '../app/core/dev/failure-switch';

export const devToolsProviders: Provider[] = [{ provide: DEV_TOOL, useValue: FailureSwitch }];

// last in the chain, closest to the network
export const devToolsInterceptors: HttpInterceptorFn[] = [failureInterceptor];
