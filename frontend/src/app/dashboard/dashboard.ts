import { DecimalPipe, TitleCasePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { BarList, BarRow } from '../shared/bar-list';
import { TrendChart } from '../shared/trend-chart';
import { errorMessage } from '../tickets/ticket-api';
import {
  CATEGORY_LABELS,
  PRIORITY_LABELS,
  STATUS_LABELS,
  TICKET_CATEGORIES,
  TICKET_PRIORITIES,
  TICKET_STATUSES,
} from '../tickets/ticket.model';
import { Dashboard as DashboardData, DashboardApi } from './dashboard-api';

/** All numbers come from GET /api/dashboard, which already restricts them to what the user may see. */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DecimalPipe, TitleCasePipe, BarList, TrendChart],
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly api = inject(DashboardApi);
  protected readonly auth = inject(AuthService);

  protected readonly data = signal<DashboardData | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly trendDays = signal(14);

  protected readonly statusRows = computed<BarRow[]>(() => {
    const d = this.data();
    return d
      ? TICKET_STATUSES.map((s) => ({ label: STATUS_LABELS[s], value: d.byStatus[s] ?? 0 }))
      : [];
  });
  protected readonly priorityRows = computed<BarRow[]>(() => {
    const d = this.data();
    return d
      ? TICKET_PRIORITIES.map((p) => ({ label: PRIORITY_LABELS[p], value: d.byPriority[p] ?? 0 }))
      : [];
  });
  protected readonly categoryRows = computed<BarRow[]>(() => {
    const d = this.data();
    return d
      ? TICKET_CATEGORIES.map((c) => ({ label: CATEGORY_LABELS[c], value: d.byCategory[c] ?? 0 }))
      : [];
  });
  protected readonly scopeText = computed(() => {
    switch (this.data()?.scope) {
      case 'all':
        return 'All tickets in the service desk.';
      case 'work-queue':
        return 'Tickets in your queues: assigned to you, your teams and the general queue.';
      default:
        return 'Tickets you raised.';
    }
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.dashboard(this.trendDays()).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected setTrendDays(value: string): void {
    this.trendDays.set(Number(value));
    this.load();
  }
}
