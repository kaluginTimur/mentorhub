import { MeUser } from '../api/models/me-user';

export function displayName(user: Pick<MeUser, 'firstName' | 'lastName' | 'email'>): string {
  const name = [user.firstName, user.lastName]
    .map((part) => part?.trim())
    .filter((part) => !!part)
    .join(' ');
  return name || user.email;
}

export function greetingName(user: Pick<MeUser, 'firstName' | 'lastName' | 'email'>): string {
  return user.firstName?.trim() || displayName(user);
}
