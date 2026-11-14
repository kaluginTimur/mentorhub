import { Component, computed, input } from '@angular/core';
import { displayName } from '../../core/auth/user-name';

export interface AvatarUser {
  id?: string | null;
  email?: string | null;
  firstName?: string | null;
  lastName?: string | null;
}

export type AvatarSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';

const PALETTE_COUNT = 8;

const SIZE_CLASSES: Record<AvatarSize, string> = {
  xs: 'size-6 text-[10px]',
  sm: 'size-8 text-xs',
  md: 'size-9 text-xs',
  lg: 'size-12 text-base',
  xl: 'size-16 text-xl',
};

const BASE_CLASSES =
  'inline-flex shrink-0 items-center justify-center rounded-full font-display font-semibold tracking-tight text-white select-none leading-none';

function firstChar(text: string): string {
  const chars = Array.from(text.trim());
  return chars[0] ?? '';
}

function hashSeed(seed: string): number {
  let hash = 0x811c9dc5;
  for (let i = 0; i < seed.length; i++) {
    hash ^= seed.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

export function extractInitials(user: AvatarUser | null | undefined): string {
  if (!user) {
    return '?';
  }
  const first = user.firstName?.trim() ?? '';
  const last = user.lastName?.trim() ?? '';
  if (first && last) {
    return (firstChar(first) + firstChar(last)).toUpperCase();
  }

  const resolved = displayName({
    firstName: first || null,
    lastName: last || null,
    email: user.email?.trim() ?? '',
  });
  if (!resolved) {
    return '?';
  }

  if (!first && !last && resolved.includes('@')) {
    const local = resolved.split('@')[0] ?? '';
    const tokens = local.split(/[._-]+/).filter(Boolean);
    if (tokens.length >= 2) {
      return (firstChar(tokens[0]) + firstChar(tokens[1])).toUpperCase();
    }
    return (firstChar(tokens[0] ?? local) || '?').toUpperCase();
  }

  const words = resolved.split(/\s+/).filter(Boolean);
  if (words.length >= 2) {
    return (firstChar(words[0]) + firstChar(words[words.length - 1])).toUpperCase();
  }
  return (firstChar(words[0] ?? resolved) || '?').toUpperCase();
}

@Component({
  selector: 'app-avatar',
  template: `{{ initials() }}`,
  host: {
    '[class]': 'hostClass()',
    '[style.background-image]': 'gradient()',
    '[style.box-shadow]': "'var(--mh-avatar-shadow)'",
    '[style.width.px]': 'pixelSize()',
    '[style.height.px]': 'pixelSize()',
    '[style.font-size.px]': 'pixelFontSize()',
    '[attr.role]': 'label() ? "img" : null',
    '[attr.aria-label]': 'label() || null',
    '[attr.aria-hidden]': 'label() ? null : "true"',
  },
})
export class Avatar {
  readonly user = input<AvatarUser | null | undefined>(null);
  readonly size = input<AvatarSize>('md');
  readonly pixelSize = input<number | null>(null);
  readonly label = input<string | null>(null);

  protected readonly initials = computed(() => extractInitials(this.user()));

  protected readonly paletteIndex = computed(() => {
    const u = this.user();
    const resolved = u
      ? displayName({
          firstName: u.firstName ?? null,
          lastName: u.lastName ?? null,
          email: u.email ?? '',
        })
      : '';
    const seed = u?.id?.trim() || u?.email?.trim() || resolved || 'mentorhub';
    return hashSeed(seed) % PALETTE_COUNT;
  });

  protected readonly gradient = computed(() => {
    const index = this.paletteIndex();
    return `linear-gradient(135deg, var(--mh-avatar-${index}-from), var(--mh-avatar-${index}-to))`;
  });

  protected readonly hostClass = computed(() =>
    this.pixelSize() ? BASE_CLASSES : `${BASE_CLASSES} ${SIZE_CLASSES[this.size()]}`,
  );

  protected readonly pixelFontSize = computed(() => {
    const px = this.pixelSize();
    return px ? Math.max(10, Math.round(px * 0.38)) : null;
  });
}
