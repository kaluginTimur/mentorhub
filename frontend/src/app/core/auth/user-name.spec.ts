import { displayName, greetingName } from './user-name';

describe('user name', () => {
  const email = 'anna@example.com';

  it('joins first and last name', () => {
    expect(displayName({ firstName: 'Anna', lastName: 'Petrova', email })).toBe('Anna Petrova');
  });

  it('uses the only name that is set', () => {
    expect(displayName({ firstName: 'Anna', lastName: null, email })).toBe('Anna');
    expect(displayName({ firstName: null, lastName: 'Petrova', email })).toBe('Petrova');
  });

  it('falls back to the email when no name is set', () => {
    expect(displayName({ firstName: null, lastName: null, email })).toBe(email);
    expect(displayName({ firstName: ' ', lastName: '', email })).toBe(email);
  });

  it('greets by first name, else by the display name', () => {
    expect(greetingName({ firstName: 'Anna', lastName: 'Petrova', email })).toBe('Anna');
    expect(greetingName({ firstName: null, lastName: 'Petrova', email })).toBe('Petrova');
    expect(greetingName({ firstName: null, lastName: null, email })).toBe(email);
  });
});
