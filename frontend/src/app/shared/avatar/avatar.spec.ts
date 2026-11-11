import styles from '../../../styles.scss' with { loader: 'text' };
import { AVATAR_TOKENS, avatarColor, initials } from './avatar';

/** The value `styles.scss` gives a custom property, as a six-digit hex color, or the raw value if it is not one. */
function tokenValue(name: string): string | undefined {
  const match = new RegExp(`${name}\\s*:\\s*([^;]+);`).exec(styles);
  if (!match) return undefined;
  const value = match[1].trim();
  const short = /^#([0-9a-f])([0-9a-f])([0-9a-f])$/i.exec(value);
  return short ? `#${short[1]}${short[1]}${short[2]}${short[2]}${short[3]}${short[3]}` : value;
}

const HEX = /^#[0-9a-f]{6}$/i;

describe('avatar', () => {
  it.each([
    ['both names', 'anna', 'petrova', 'AP'],
    ['only the first name', 'Anna', null, 'A'],
    ['only the last name', null, 'Petrova', 'P'],
    ['no names: the email', null, ' zed@example.com', 'Z'],
  ])('builds initials from %s', (_case, firstName, lastName, expected) => {
    const email = _case.startsWith('no names') ? 'zed@example.com' : 'x@example.com';
    expect(initials({ firstName, lastName, email })).toBe(expected);
  });

  const sampleIds = Array.from(
    { length: 35 },
    (_, i) => `00000000-0000-4000-a000-000000000${101 + i}`,
  );

  function luminance(hex: string): number {
    const [r, g, b] = [1, 3, 5].map((i) => {
      const c = parseInt(hex.slice(i, i + 2), 16) / 255;
      return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  function contrast(a: string, b: string): number {
    const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
    return (light + 0.05) / (dark + 0.05);
  }

  it('names the ten palette tokens', () => {
    expect([...AVATAR_TOKENS]).toEqual(
      Array.from({ length: 10 }, (_, i) => `--app-avatar-${i + 1}`),
    );
  });

  it('gives the same palette color for the same id', () => {
    for (const id of ['u1', '', ...sampleIds]) expect(avatarColor(id)).toBe(avatarColor(id));
    for (const id of ['u1', '', ...sampleIds]) {
      expect(AVATAR_TOKENS.map((token) => `var(${token})`)).toContain(avatarColor(id));
    }
  });

  it('spreads sequential ids over the palette', () => {
    expect(new Set(sampleIds.map(avatarColor)).size).toBeGreaterThanOrEqual(8);
  });

  it('defines every palette token in the global styles as one distinct hex color for both schemes', () => {
    const values = AVATAR_TOKENS.map((token) => tokenValue(token));
    values.forEach((value, i) => expect(value, AVATAR_TOKENS[i]).toMatch(HEX));
    expect(new Set(values.map((v) => v?.toLowerCase())).size).toBe(10);
  });

  it('keeps white initials readable on every palette color', () => {
    const onColor = tokenValue('--app-avatar-on-color');
    expect(onColor?.toLowerCase()).toBe('#ffffff');
    for (const token of AVATAR_TOKENS) {
      const color = tokenValue(token);
      expect(color, token).toMatch(HEX);
      expect(contrast(color!, onColor!), `${token} ${color}`).toBeGreaterThanOrEqual(4.5);
    }
  });
});
