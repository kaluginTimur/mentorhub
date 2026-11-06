import { HttpInterceptorFn } from '@angular/common/http';
import { Provider } from '@angular/core';
import { DEV_TOOL } from '../app/core/dev/dev-tool';
import { failureInterceptor } from '../app/core/dev/failure-interceptor';
import { FailureSwitch } from '../app/core/dev/failure-switch';

export const devToolsProviders: Provider[] = [{ provide: DEV_TOOL, useValue: FailureSwitch }];

// last in the chain, closest to the network
export const devToolsInterceptors: HttpInterceptorFn[] = [failureInterceptor];
