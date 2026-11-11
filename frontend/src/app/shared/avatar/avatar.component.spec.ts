import { TestBed } from '@angular/core/testing';
import { Avatar } from './avatar.component';
import { avatarColor } from './avatar';

describe('Avatar', () => {
  function render(id: string, size?: number) {
    const fixture = TestBed.createComponent(Avatar);
    fixture.componentRef.setInput('user', {
      id,
      firstName: 'Anna',
      lastName: 'Petrova',
      email: 'anna@example.com',
    });
    if (size) fixture.componentRef.setInput('size', size);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the initials, hides itself from screen readers and takes its color from the id', () => {
    const el = render('u1', 40);
    expect(el.textContent).toBe('AP');
    expect(el.getAttribute('aria-hidden')).toBe('true');
    expect(el.style.getPropertyValue('--avatar-color')).toBe(avatarColor('u1'));
    expect(el.style.getPropertyValue('--avatar-color')).toMatch(/^var\(--app-avatar-(10|[1-9])\)$/);
    expect(el.style.getPropertyValue('--avatar-size')).toBe('40px');
  });

  it('keeps the same color for the same id', () => {
    expect(render('u1').style.getPropertyValue('--avatar-color')).toBe(
      render('u1').style.getPropertyValue('--avatar-color'),
    );
  });
});
