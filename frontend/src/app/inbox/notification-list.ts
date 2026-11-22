import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { NotificationService } from '../shared/notification';
import { errorMessage } from '../tickets/ticket-api';
import { Page } from '../tickets/ticket.model';
import { InboxNotification, InboxService } from './inbox.service';

@Component({
  selector: 'app-notification-list',
  imports: [DatePipe],
  template: `
    <div class="page-header">
      <h1>Notifications</h1>
      <div class="header-actions">
        <label class="checkbox"
          ><input
            type="checkbox"
            #unread
            [checked]="unreadOnly()"
            (change)="setUnreadOnly(unread.checked)"
          />
          Unread only</label
        >
        <button type="button" class="btn" [disabled]="inbox.unread() === 0" (click)="markAll()">
          Mark all as read
        </button>
      </div>
    </div>
    @if (error()) {
      <div class="panel panel-error" role="alert">
        <p>{{ error() }}</p>
        <button type="button" class="btn" (click)="load()">Try again</button>
      </div>
    } @else if (page(); as p) {
      <ul class="notification-list card card-flush">
        @for (n of p.content; track n.id) {
          <li [class.unread]="!n.read">
            <button type="button" class="notification-item" (click)="open(n)">
              <span class="notification-message">{{ n.message }}</span>
              <span class="muted small">{{ n.createdAt | date: 'dd MMM yyyy, HH:mm' }}</span>
            </button>
            @if (!n.read) {
              <button type="button" class="btn btn-link btn-sm" (click)="markRead(n)">
                Mark read
              </button>
            }
          </li>
        } @empty {
          <li class="muted empty-row">
            {{ unreadOnly() ? 'No unread notifications.' : 'No notifications yet.' }}
          </li>
        }
      </ul>
      @if (p.totalPages > 1) {
        <nav class="pager" aria-label="Notification pages">
          <button type="button" class="btn btn-sm" [disabled]="!p.hasPrevious" (click)="go(-1)">
            Previous
          </button>
          <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages }}</span>
          <button type="button" class="btn btn-sm" [disabled]="!p.hasNext" (click)="go(1)">
            Next
          </button>
        </nav>
      }
    } @else {
      <p class="muted" aria-busy="true">Loading notifications…</p>
    }
  `,
})
export class NotificationList {
  protected readonly inbox = inject(InboxService);
  private readonly router = inject(Router);
  private readonly toasts = inject(NotificationService);
  protected readonly page = signal<Page<InboxNotification> | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly unreadOnly = signal(false);
  private pageNumber = 0;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.inbox.list(this.pageNumber, 20, this.unreadOnly()).subscribe({
      next: (p) => this.page.set(p),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected setUnreadOnly(value: boolean): void {
    this.unreadOnly.set(value);
    this.pageNumber = 0;
    this.load();
  }

  protected go(delta: number): void {
    this.pageNumber = Math.max(0, this.pageNumber + delta);
    this.load();
  }

  protected markRead(n: InboxNotification): void {
    this.inbox.markRead(n.id).subscribe({
      next: (updated) => this.replace(updated),
      error: (e) => this.toasts.error(errorMessage(e)),
    });
  }

  protected markAll(): void {
    this.inbox
      .markAllRead()
      .subscribe({ next: () => this.load(), error: (e) => this.toasts.error(errorMessage(e)) });
  }

  /** Marks the notification read and opens its ticket. */
  protected open(n: InboxNotification): void {
    const go = () => {
      if (n.ticketId !== null) {
        void this.router.navigate(['/tickets', n.ticketId]);
      }
    };
    if (n.read) {
      go();
      return;
    }
    this.inbox
      .markRead(n.id)
      .subscribe({ next: (u) => (this.replace(u), go()), error: () => go() });
  }

  private replace(updated: InboxNotification): void {
    this.page.update((p) =>
      p ? { ...p, content: p.content.map((x) => (x.id === updated.id ? updated : x)) } : p,
    );
  }
}
