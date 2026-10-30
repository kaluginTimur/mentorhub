import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { ConfirmDialog, ConfirmDialogData } from './confirm-dialog';

describe('ConfirmDialog', () => {
  afterEach(() => TestBed.inject(MatDialog).closeAll());

  async function open(data: ConfirmDialogData) {
    const ref = TestBed.inject(MatDialog).open(ConfirmDialog, { data });
    const closed = firstValueFrom(ref.afterClosed());
    await new Promise((resolve) => setTimeout(resolve));
    const root = document.querySelector('app-confirm-dialog') as HTMLElement;
    const button = (label: string) =>
      [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === label)!;
    return { root, closed, button };
  }

  const base = { title: 'Revoke it?', text: 'The link will stop working.', confirmLabel: 'Revoke' };

  it('shows the title and text and closes with true without a checkbox', async () => {
    const { root, closed, button } = await open(base);
    expect(root.textContent).toContain('Revoke it?');
    expect(root.textContent).toContain('The link will stop working.');
    expect(root.querySelector('mat-checkbox')).toBeNull();
    button('Revoke').click();
    expect(await closed).toBe(true);
  });

  it('closes with the checkbox state', async () => {
    const { root, closed, button } = await open({
      ...base,
      checkbox: { label: 'Send the new link by email', checked: true },
    });
    const box = root.querySelector('mat-checkbox') as HTMLElement;
    expect(box.textContent).toContain('Send the new link by email');
    const input = box.querySelector('input') as HTMLInputElement;
    expect(input.checked).toBe(true);
    input.click();
    button('Revoke').click();
    expect(await closed).toEqual({ checked: false });
  });

  it('closes with the initial checkbox state when untouched', async () => {
    const { closed, button } = await open({
      ...base,
      checkbox: { label: 'Send', checked: false },
    });
    button('Revoke').click();
    expect(await closed).toEqual({ checked: false });
  });

  it('closes with undefined on Cancel', async () => {
    const { closed, button } = await open({ ...base, checkbox: { label: 'Send', checked: true } });
    button('Cancel').click();
    expect(await closed).toBeUndefined();
  });
});
