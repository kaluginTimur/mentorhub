import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Email } from './email';

@Component({ imports: [Email], template: `<app-email value="ada.lovelace@example.co.uk" />` })
class Host {}

describe('Email', () => {
  it('keeps the address as it is and offers a line break after "@" and each dot', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const email = fixture.nativeElement.querySelector('app-email') as HTMLElement;
    expect(email.textContent).toBe('ada.lovelace@example.co.uk');
    expect(email.querySelectorAll('wbr')).toHaveLength(5);
  });
});
