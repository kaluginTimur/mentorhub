import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { Api } from './core/api/api';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        {
          provide: Api,
          useValue: { invoke: () => Promise.resolve({ name: 'MentorHub', version: '1.2.3' }) },
        },
      ],
    }).compileComponents();
  });

  it('shows application name and version from the backend', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('MentorHub · version 1.2.3');
  });
});
