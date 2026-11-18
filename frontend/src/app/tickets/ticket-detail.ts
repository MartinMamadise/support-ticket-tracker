import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NotificationService } from '../shared/notification';
import { TicketBadge } from '../shared/ticket-badge';
import { TicketApi, errorMessage } from './ticket-api';
import { Ticket } from './ticket.model';

@Component({
  selector: 'app-ticket-detail',
  imports: [RouterLink, DatePipe, TicketBadge],
  templateUrl: './ticket-detail.html',
})
export class TicketDetail {
  private readonly api = inject(TicketApi);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly id = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));

  protected readonly ticket = signal<Ticket | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly confirmingDelete = signal(false);

  constructor() {
    if (!Number.isInteger(this.id) || this.id <= 0) {
      this.loadError.set('Invalid ticket number.');
      this.loading.set(false);
      return;
    }
    this.api.get(this.id).subscribe({
      next: (ticket) => {
        this.ticket.set(ticket);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected setConfirming(value: boolean): void {
    this.confirmingDelete.set(value);
  }

  protected delete(): void {
    this.api.delete(this.id).subscribe({
      next: () => {
        this.notifications.success(`Ticket #${this.id} deleted.`);
        void this.router.navigate(['/tickets']);
      },
      error: (err) => {
        this.confirmingDelete.set(false);
        this.notifications.error(errorMessage(err));
      },
    });
  }
}
