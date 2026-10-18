import { getClientConfig } from '../api/fn/system/get-client-config';
import { previewInvitation } from '../api/fn/invitations/preview-invitation';
import { getSystemInfo } from '../api/fn/system/get-system-info';

const PUBLIC_PATHS: string[] = [getSystemInfo.PATH, getClientConfig.PATH, previewInvitation.PATH];

function pathOf(url: string): string | null {
  const parsed = new URL(url, globalThis.location.origin);
  return parsed.origin === globalThis.location.origin ? parsed.pathname : null;
}

export function isApiRequest(url: string): boolean {
  return pathOf(url)?.startsWith('/api/') ?? false;
}

export function isPublicApiRequest(url: string): boolean {
  const path = pathOf(url);
  return path !== null && PUBLIC_PATHS.includes(path);
}

const CALLBACK_PARAMS = [
  'code',
  'state',
  'session_state',
  'iss',
  'error',
  'error_description',
  'error_uri',
];

/** The URL without the OIDC callback parameters, as path, query and fragment. */
export function stripCallbackParams(url: string): string {
  const parsed = new URL(url, globalThis.location.origin);
  CALLBACK_PARAMS.forEach((name) => parsed.searchParams.delete(name));
  return parsed.pathname + parsed.search + parsed.hash;
}

/** True for the URL the identity provider redirects back to. */
export function isCallbackUrl(url: string): boolean {
  return /[?&](code|error)=/.test(url);
}
