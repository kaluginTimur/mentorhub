import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Avatar, AvatarSize, AvatarUser, extractInitials } from './avatar';

@Component({
  imports: [Avatar],
  template: `<app-avatar
    [user]="user()"
    [size]="size()"
    [pixelSize]="pixelSize()"
    [label]="label()"
  />`,
})
class Host {
  readonly user = signal<AvatarUser | null>(null);
  readonly size = signal<AvatarSize>('md');
  readonly pixelSize = signal<number | null>(null);
  readonly label = signal<string | null>(null);
}

describe('Avatar', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Host] });
  });

  it.each([
    [
      'both first and last name',
      { firstName: 'Anna', lastName: 'Petrova', email: 'a@b.com' },
      'AP',
    ],
    ['only first name', { firstName: 'Anna', lastName: null, email: 'a@b.com' }, 'A'],
    ['multi-word first name', { firstName: 'Anna Maria', lastName: null, email: 'a@b.com' }, 'AM'],
    ['only last name', { firstName: null, lastName: 'Petrova', email: 'a@b.com' }, 'P'],
    [
      'dotted email fallback',
      { firstName: null, lastName: null, email: 'anna.petrova@example.com' },
      'AP',
    ],
    ['plain email fallback', { firstName: null, lastName: null, email: 'anna@example.com' }, 'A'],
    ['null user', null, '?'],
    ['empty user', { firstName: '  ', lastName: null, email: '' }, '?'],
  ])('extracts initials for %s', (_case, user, expected) => {
    expect(extractInitials(user)).toBe(expected);
  });

  it('renders initials, deterministic gradient, and size classes', async () => {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.user.set({
      id: 'user-42',
      firstName: 'Anna',
      lastName: 'Petrova',
      email: 'anna@example.com',
    });
    fixture.componentInstance.size.set('lg');
    await fixture.whenStable();

    const avatar = (fixture.nativeElement as HTMLElement).querySelector(
      'app-avatar',
    ) as HTMLElement;
    expect(avatar.textContent?.trim()).toBe('AP');
    expect(avatar.className).toContain('size-12');
    expect(avatar.style.backgroundImage).toMatch(
      /^linear-gradient\(135deg, var\(--mh-avatar-\d-from\), var\(--mh-avatar-\d-to\)\)$/,
    );
    expect(avatar.getAttribute('aria-hidden')).toBe('true');

    const firstGradient = avatar.style.backgroundImage;

    fixture.componentInstance.size.set('sm');
    await fixture.whenStable();
    expect(avatar.className).toContain('size-8');
    expect(avatar.style.backgroundImage).toBe(firstGradient);
  });

  it('supports explicit pixel sizes and accessible img labels', async () => {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.user.set({
      id: 'user-7',
      firstName: 'Boris',
      lastName: 'Sokolov',
      email: 'boris@example.com',
    });
    fixture.componentInstance.pixelSize.set(50);
    fixture.componentInstance.label.set('Boris Sokolov');
    await fixture.whenStable();

    const avatar = (fixture.nativeElement as HTMLElement).querySelector(
      'app-avatar',
    ) as HTMLElement;
    expect(avatar.style.width).toBe('50px');
    expect(avatar.style.height).toBe('50px');
    expect(avatar.getAttribute('role')).toBe('img');
    expect(avatar.getAttribute('aria-label')).toBe('Boris Sokolov');
    expect(avatar.getAttribute('aria-hidden')).toBeNull();
  });
});
