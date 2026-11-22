import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { toParams } from '../tickets/ticket-api';
import { Page } from '../tickets/ticket.model';

export type InboxNotificationType =
  | 'TICKET_ASSIGNED'
  | 'TICKET_UNASSIGNED'
  | 'TEAM_ASSIGNED'
  | 'COMMENT_ADDED'
  | 'INTERNAL_NOTE_ADDED'
  | 'STATUS_CHANGED'
  | 'TICKET_RESOLVED'
  | 'TICKET_REOPENED'
  | 'SLA_WARNING'
  | 'SLA_BREACHED';

/** A persistent notification stored by the server (not a toast). */
export interface InboxNotification {
  id: number;
  type: InboxNotificationType;
  ticketId: number | null;
  message: string;
  createdAt: string;
  readAt: string | null;
  read: boolean;
}

/** How often the unread badge is refreshed while signed in. */
export const UNREAD_POLL_MS = 60_000;

/**
 * Persistent in-app notifications (`/api/notifications`). Separate from {@link NotificationService} in shared/, which
 * shows short-lived toast messages. Keeps the unread count for the header badge.
 */
@Injectable({ providedIn: 'root' })
export class InboxService {
  private readonly http = inject(HttpClient);
  private readonly _unread = signal(0);
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly unread = this._unread.asReadonly();

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopPolling());
  }

  list(page = 0, size = 20, unreadOnly = false): Observable<Page<InboxNotification>> {
    return this.http.get<Page<InboxNotification>>('/api/notifications', {
      params: toParams({ page, size, unreadOnly }),
    });
  }

  refreshUnread(): void {
    this.http.get<{ count: number }>('/api/notifications/unread-count').subscribe({
      next: (r) => this._unread.set(r.count),
      error: () => {
        // A failed poll is not worth a message; the next one will try again.
      },
    });
  }

  markRead(id: number): Observable<InboxNotification> {
    return this.http
      .put<InboxNotification>(`/api/notifications/${id}/read`, {})
      .pipe(tap(() => this._unread.update((n) => Math.max(0, n - 1))));
  }

  markAllRead(): Observable<{ updated: number }> {
    return this.http
      .put<{ updated: number }>('/api/notifications/read-all', {})
      .pipe(tap(() => this._unread.set(0)));
  }

  startPolling(): void {
    this.refreshUnread();
    if (this.timer === null) {
      this.timer = setInterval(() => this.refreshUnread(), UNREAD_POLL_MS);
    }
  }

  stopPolling(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this._unread.set(0);
  }
}
