import { Component, computed, input } from '@angular/core';
import {
  CATEGORY_LABELS,
  PRIORITY_LABELS,
  STATUS_LABELS,
  TicketCategory,
  TicketPriority,
  TicketStatus,
} from '../tickets/ticket.model';

/** Small text label for a ticket's status or priority. */
@Component({
  selector: 'app-ticket-badge',
  template: `<span class="badge" [class]="cssClass()">{{ label() }}</span>`,
})
export class TicketBadge {
  readonly kind = input.required<'status' | 'priority' | 'category'>();
  readonly value = input.required<TicketStatus | TicketPriority | TicketCategory>();

  protected readonly label = computed(() => {
    const value = this.value();
    const kind = this.kind();
    const labels: Record<string, string> =
      kind === 'status' ? STATUS_LABELS : kind === 'priority' ? PRIORITY_LABELS : CATEGORY_LABELS;
    return labels[value] ?? value;
  });

  protected readonly cssClass = computed(
    () => `${this.kind()}-${this.value().toLowerCase().replace('_', '-')}`,
  );
}
