/** Up to two capital letters from the first and last name, else the first letter of the email. */
export function initials(user: {
  firstName?: string | null;
  lastName?: string | null;
  email: string;
}): string {
  const letters = [user.firstName, user.lastName]
    .map((n) => n?.trim().charAt(0).toUpperCase())
    .filter((c): c is string => !!c);
  if (letters.length > 0) return letters.join('');
  return user.email.trim().charAt(0).toUpperCase() || '?';
}

/** The custom properties of the palette; the colors are defined in `styles.scss`. */
export const AVATAR_TOKENS = [
  '--app-avatar-1',
  '--app-avatar-2',
  '--app-avatar-3',
  '--app-avatar-4',
  '--app-avatar-5',
  '--app-avatar-6',
  '--app-avatar-7',
  '--app-avatar-8',
  '--app-avatar-9',
  '--app-avatar-10',
] as const;

/** A palette color (a `var()` of its token) that is the same for the same id, so a user keeps their color on every device. */
export function avatarColor(id: string): string {
  // FNV-1a, then the murmur3 finalizer: similar (sequential) ids still end up unrelated.
  let h = 0x811c9dc5;
  for (let i = 0; i < id.length; i++) h = Math.imul(h ^ id.charCodeAt(i), 0x01000193);
  h ^= h >>> 16;
  h = Math.imul(h, 0x85ebca6b);
  h ^= h >>> 13;
  h = Math.imul(h, 0xc2b2ae35);
  h ^= h >>> 16;
  return `var(${AVATAR_TOKENS[(h >>> 0) % AVATAR_TOKENS.length]})`;
}
