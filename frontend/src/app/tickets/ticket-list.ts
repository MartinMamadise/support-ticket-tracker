import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NotificationService } from '../shared/notification';
import { TicketBadge } from '../shared/ticket-badge';
import { TicketApi, errorMessage } from './ticket-api';
import { STATUS_LABELS, TICKET_STATUSES, Ticket, TicketStatus } from './ticket.model';

@Component({
  selector: 'app-ticket-list',
  imports: [RouterLink, DatePipe, TicketBadge],
  templateUrl: './ticket-list.html',
})
export class TicketList {
  private readonly api = inject(TicketApi);
  private readonly notifications = inject(NotificationService);

  protected readonly statuses = TICKET_STATUSES;
  protected readonly statusLabels = STATUS_LABELS;

  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly search = signal('');
  protected readonly statusFilter = signal<TicketStatus | ''>('');
  /** Id of the ticket whose delete button was clicked and awaits confirmation. */
  protected readonly confirmingId = signal<number | null>(null);

  /** Tickets after applying the search box and status filter, newest first. */
  protected readonly visibleTickets = computed(() => {
    const term = this.search().trim().toLowerCase();
    const status = this.statusFilter();
    return this.tickets()
      .filter((t) => !status || t.status === status)
      .filter(
        (t) =>
          !term ||
          t.title.toLowerCase().includes(term) ||
          String(t.id) === term,
      )
      .sort((a, b) => b.id - a.id);
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api.list().subscribe({
      next: (tickets) => {
        this.tickets.set(tickets);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected setStatusFilter(value: string): void {
    this.statusFilter.set(value as TicketStatus | '');
  }

  protected clearFilters(): void {
    this.search.set('');
    this.statusFilter.set('');
  }

  protected confirmDelete(id: number): void {
    this.confirmingId.set(id);
  }

  protected cancelDelete(): void {
    this.confirmingId.set(null);
  }

  protected delete(ticket: Ticket): void {
    this.api.delete(ticket.id).subscribe({
      next: () => {
        this.tickets.update((list) => list.filter((t) => t.id !== ticket.id));
        this.confirmingId.set(null);
        this.notifications.success(`Ticket #${ticket.id} deleted.`);
      },
      error: (err) => {
        this.confirmingId.set(null);
        this.notifications.error(errorMessage(err));
        // The ticket may already be gone (404); refresh so the list is accurate.
        this.load();
      },
    });
  }
}
