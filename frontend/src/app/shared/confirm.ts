import {
  Component,
  ElementRef,
  Injectable,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';

interface PendingConfirm {
  title: string;
  message: string;
  confirmLabel: string;
  danger: boolean;
  resolve: (ok: boolean) => void;
}

/** Asks the user to confirm an action in an accessible modal dialog. Only one dialog at a time. */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly pending = signal<PendingConfirm | null>(null);

  ask(options: {
    title: string;
    message: string;
    confirmLabel?: string;
    danger?: boolean;
  }): Promise<boolean> {
    this.pending()?.resolve(false);
    return new Promise((resolve) =>
      this.pending.set({
        title: options.title,
        message: options.message,
        confirmLabel: options.confirmLabel ?? 'Confirm',
        danger: options.danger ?? false,
        resolve,
      }),
    );
  }

  answer(ok: boolean): void {
    const current = this.pending();
    this.pending.set(null);
    current?.resolve(ok);
  }
}

/** Rendered once in the app shell. Escape cancels; focus moves to the safe (cancel) button when it opens. */
@Component({
  selector: 'app-confirm-dialog',
  template: `
    @if (service.pending(); as p) {
      <div class="dialog-backdrop" (click)="service.answer(false)"></div>
      <div
        class="dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
        aria-describedby="confirm-message"
        (keydown.escape)="service.answer(false)"
      >
        <h2 id="confirm-title">{{ p.title }}</h2>
        <p id="confirm-message">{{ p.message }}</p>
        <div class="dialog-actions">
          <button #cancel type="button" class="btn" (click)="service.answer(false)">Cancel</button>
          <button
            type="button"
            class="btn"
            [class.btn-danger]="p.danger"
            [class.btn-primary]="!p.danger"
            (click)="service.answer(true)"
          >
            {{ p.confirmLabel }}
          </button>
        </div>
      </div>
    }
  `,
})
export class ConfirmDialog {
  protected readonly service = inject(ConfirmService);
  private readonly cancel = viewChild<ElementRef<HTMLButtonElement>>('cancel');

  constructor() {
    effect(() => {
      this.cancel()?.nativeElement.focus();
    });
  }
}
