import { displayName, greetingName } from './user-name';

describe('user name', () => {
  const email = 'anna@example.com';

  // the cases of IDN-11.1 are tested through the shell; this is the detail it does not show
  it('treats blank names as not set', () => {
    expect(displayName({ firstName: ' ', lastName: '', email })).toBe(email);
  });

  it('greets by first name, else by the display name', () => {
    expect(greetingName({ firstName: 'Anna', lastName: 'Petrova', email })).toBe('Anna');
    expect(greetingName({ firstName: null, lastName: 'Petrova', email })).toBe('Petrova');
    expect(greetingName({ firstName: null, lastName: null, email })).toBe(email);
  });
});
