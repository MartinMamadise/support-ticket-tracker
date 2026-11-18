import { Injectable, signal } from '@angular/core';

export interface Notice {
  id: number;
  kind: 'success' | 'error';
  text: string;
}

/** Holds the short feedback messages shown at the top of the page. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private nextId = 1;
  private readonly _notices = signal<Notice[]>([]);
  readonly notices = this._notices.asReadonly();

  success(text: string): void {
    this.add('success', text, 5000);
  }

  error(text: string): void {
    this.add('error', text, 8000);
  }

  dismiss(id: number): void {
    this._notices.update((list) => list.filter((n) => n.id !== id));
  }

  private add(kind: Notice['kind'], text: string, timeoutMs: number): void {
    const id = this.nextId++;
    this._notices.update((list) => [...list, { id, kind, text }]);
    setTimeout(() => this.dismiss(id), timeoutMs);
  }
}
