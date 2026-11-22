import { TestBed } from '@angular/core/testing';
import { ConfirmDialog, ConfirmService } from './confirm';

describe('ConfirmService and dialog', () => {
  it('resolves with the answer and closes the dialog', async () => {
    TestBed.configureTestingModule({ imports: [ConfirmDialog] });
    const service = TestBed.inject(ConfirmService);
    const fixture = TestBed.createComponent(ConfirmDialog);
    const answer = service.ask({
      title: 'Delete?',
      message: 'Gone for good',
      confirmLabel: 'Delete',
      danger: true,
    });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role=alertdialog]')?.textContent).toContain('Gone for good');
    expect(document.activeElement?.textContent?.trim()).toBe('Cancel');

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Delete')!
      .click();
    await expect(answer).resolves.toBe(true);
    fixture.detectChanges();
    expect(el.querySelector('[role=alertdialog]')).toBeNull();
  });

  it('a second question cancels the first', async () => {
    const service = TestBed.inject(ConfirmService);
    const first = service.ask({ title: 'A', message: 'a' });
    service.ask({ title: 'B', message: 'b' });
    await expect(first).resolves.toBe(false);
  });
});
