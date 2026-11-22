import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AdminApi, Team } from '../admin/admin-api';
import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../shared/notification';
import { SlaBadge } from '../shared/sla-badge';
import { TicketBadge } from '../shared/ticket-badge';
import { TicketApi, errorMessage } from './ticket-api';
import {
  CATEGORY_LABELS,
  PRIORITY_LABELS,
  STATUS_LABELS,
  TICKET_CATEGORIES,
  TICKET_PRIORITIES,
  TICKET_STATUSES,
  Page,
  Ticket,
  TicketCategory,
  TicketListParams,
  TicketPriority,
  TicketStatus,
  TicketView,
  UserSummary,
} from './ticket.model';

/** Heading and empty-state text per queue (route data `view`). */
export const VIEW_TITLES: Record<TicketView, string> = {
  all: 'All tickets',
  mine: 'My tickets',
  assigned: 'Assigned to me',
  team: 'Team tickets',
  unassigned: 'Unassigned tickets',
};

/** Sort choices offered in the list; each maps to the API's sortBy and direction parameters. */
export const SORT_OPTIONS = [
  { key: 'newest', label: 'Newest first', sortBy: 'createdAt', direction: 'desc' },
  { key: 'oldest', label: 'Oldest first', sortBy: 'createdAt', direction: 'asc' },
  { key: 'updated', label: 'Recently updated', sortBy: 'updatedAt', direction: 'desc' },
  { key: 'priority', label: 'Highest priority', sortBy: 'priority', direction: 'desc' },
  { key: 'status', label: 'Status', sortBy: 'status', direction: 'asc' },
  { key: 'due', label: 'Due soonest', sortBy: 'resolutionDueAt', direction: 'asc' },
] as const satisfies readonly {
  key: string;
  label: string;
  sortBy: NonNullable<TicketListParams['sortBy']>;
  direction: 'asc' | 'desc';
}[];

type SortKey = (typeof SORT_OPTIONS)[number]['key'];

@Component({
  selector: 'app-ticket-list',
  imports: [RouterLink, DatePipe, TicketBadge, SlaBadge],
  templateUrl: './ticket-list.html',
})
export class TicketList {
  private readonly api = inject(TicketApi);
  private readonly notifications = inject(NotificationService);
  private readonly adminApi = inject(AdminApi);
  protected readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private inFlight?: Subscription;

  /** Which queue this page shows; set by the route (e.g. /tickets/mine). */
  protected readonly view: TicketView =
    (this.route.snapshot.data['view'] as TicketView | undefined) ?? 'all';
  protected readonly title = VIEW_TITLES[this.view];
  protected readonly categories = TICKET_CATEGORIES;
  protected readonly categoryLabels = CATEGORY_LABELS;
  protected readonly teams = signal<Team[]>([]);
  protected readonly agents = signal<UserSummary[]>([]);

  protected readonly statuses = TICKET_STATUSES;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly priorities = TICKET_PRIORITIES;
  protected readonly priorityLabels = PRIORITY_LABELS;
  protected readonly sortOptions = SORT_OPTIONS;

  /** The page currently shown (null until the first response arrives). */
  protected readonly result = signal<Page<Ticket> | null>(null);
  protected readonly tickets = computed(() => this.result()?.content ?? []);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  /** Search text being typed; only sent to the server when the search form is submitted. */
  protected readonly searchInput = signal('');
  /** Query values the list is currently showing. Changing a filter or sort goes back to page 1. */
  protected readonly search = signal('');
  protected readonly statusFilter = signal<TicketStatus | ''>('');
  protected readonly priorityFilter = signal<TicketPriority | ''>('');
  protected readonly categoryFilter = signal<TicketCategory | ''>('');
  protected readonly teamFilter = signal<number | null>(null);
  protected readonly agentFilter = signal<number | null>(null);
  protected readonly createdFrom = signal('');
  protected readonly createdTo = signal('');
  protected readonly sortKey = signal<SortKey>('newest');
  protected readonly page = signal(0);

  protected readonly filtersActive = computed(
    () =>
      !!this.search() ||
      !!this.statusFilter() ||
      !!this.priorityFilter() ||
      !!this.categoryFilter() ||
      this.teamFilter() !== null ||
      this.agentFilter() !== null ||
      !!this.createdFrom() ||
      !!this.createdTo(),
  );
  /** True when the server has no tickets at all (no filter applied and nothing returned). */
  protected readonly noTicketsAtAll = computed(
    () => this.result() !== null && this.result()!.totalElements === 0 && !this.filtersActive(),
  );
  /** 1-based number of the first and last ticket on this page, for "Showing 21-40 of 57". */
  protected readonly firstShown = computed(() => {
    const r = this.result();
    return r && r.content.length > 0 ? r.page * r.size + 1 : 0;
  });
  protected readonly lastShown = computed(() => {
    const r = this.result();
    return r && r.content.length > 0 ? r.page * r.size + r.content.length : 0;
  });

  /** Id of the ticket whose delete button was clicked and awaits confirmation. */
  protected readonly confirmingId = signal<number | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.inFlight?.unsubscribe());
    // A link such as the dashboard's "Open" card may preselect a status.
    const status = this.route.snapshot.queryParamMap.get('status');
    if (status && (TICKET_STATUSES as readonly string[]).includes(status)) {
      this.statusFilter.set(status as TicketStatus);
    }
    if (this.auth.isStaff()) {
      this.adminApi
        .teams()
        .subscribe({ next: (t) => this.teams.set(t), error: () => this.teams.set([]) });
      this.adminApi
        .assignable()
        .subscribe({ next: (a) => this.agents.set(a), error: () => this.agents.set([]) });
    }
    this.load();
  }

  protected load(): void {
    const sort = SORT_OPTIONS.find((o) => o.key === this.sortKey()) ?? SORT_OPTIONS[0];
    const status = this.statusFilter();
    const priority = this.priorityFilter();
    const category = this.categoryFilter();
    const params: TicketListParams = {
      page: this.page(),
      sortBy: sort.sortBy,
      direction: sort.direction,
      q: this.search().trim() || undefined,
      status: status ? [status] : undefined,
      priority: priority ? [priority] : undefined,
      category: category ? [category] : undefined,
      view: this.view === 'all' ? undefined : this.view,
      teamId: this.teamFilter() ?? undefined,
      assigneeId: this.agentFilter() ?? undefined,
      createdFrom: this.createdFrom() || undefined,
      createdTo: this.createdTo() || undefined,
    };

    this.loading.set(true);
    this.loadError.set(null);
    // Only the latest request matters: drop a slower, older one so it cannot overwrite newer results.
    this.inFlight?.unsubscribe();
    this.inFlight = this.api.list(params).subscribe({
      next: (result) => {
        // The page may have vanished (e.g. last ticket on it deleted): step back to the last real page.
        if (result.content.length === 0 && result.page > 0 && result.totalPages > 0) {
          this.page.set(result.totalPages - 1);
          this.load();
          return;
        }
        // Keep the page number in step with what the server actually returned.
        this.page.set(result.page);
        this.result.set(result);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected applySearch(event: Event): void {
    event.preventDefault();
    this.search.set(this.searchInput().trim());
    this.restartFromFirstPage();
  }

  protected setStatusFilter(value: string): void {
    this.statusFilter.set(value as TicketStatus | '');
    this.restartFromFirstPage();
  }

  protected setPriorityFilter(value: string): void {
    this.priorityFilter.set(value as TicketPriority | '');
    this.restartFromFirstPage();
  }

  protected setCategoryFilter(value: string): void {
    this.categoryFilter.set(value as TicketCategory | '');
    this.restartFromFirstPage();
  }

  protected setTeamFilter(value: string): void {
    this.teamFilter.set(value ? Number(value) : null);
    this.restartFromFirstPage();
  }

  protected setAgentFilter(value: string): void {
    this.agentFilter.set(value ? Number(value) : null);
    this.restartFromFirstPage();
  }

  protected setCreatedFrom(value: string): void {
    this.createdFrom.set(value);
    this.restartFromFirstPage();
  }

  protected setCreatedTo(value: string): void {
    this.createdTo.set(value);
    this.restartFromFirstPage();
  }

  protected setSort(value: string): void {
    this.sortKey.set(value as SortKey);
    this.restartFromFirstPage();
  }

  protected clearFilters(): void {
    this.searchInput.set('');
    this.search.set('');
    this.statusFilter.set('');
    this.priorityFilter.set('');
    this.categoryFilter.set('');
    this.teamFilter.set(null);
    this.agentFilter.set(null);
    this.createdFrom.set('');
    this.createdTo.set('');
    this.restartFromFirstPage();
  }

  protected previousPage(): void {
    if (this.result()?.hasPrevious) {
      this.page.update((p) => p - 1);
      this.load();
    }
  }

  protected nextPage(): void {
    if (this.result()?.hasNext) {
      this.page.update((p) => p + 1);
      this.load();
    }
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
        this.confirmingId.set(null);
        this.notifications.success(`Ticket #${ticket.id} deleted.`);
        // Reload so the page, totals and page count reflect the deletion.
        this.load();
      },
      error: (err) => {
        this.confirmingId.set(null);
        this.notifications.error(errorMessage(err));
        // The ticket may already be gone (404); refresh so the list is accurate.
        this.load();
      },
    });
  }

  private restartFromFirstPage(): void {
    this.page.set(0);
    this.load();
  }
}
