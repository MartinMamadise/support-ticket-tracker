import { Component, computed, input } from '@angular/core';
import { SlaStatus } from '../tickets/ticket.model';
import { formatRemaining } from './format';

export const SLA_LABELS: Record<SlaStatus, string> = {
  ON_TRACK: 'On track',
  AT_RISK: 'At risk',
  BREACHED: 'Breached',
  MET: 'Met',
  PAUSED: 'Paused',
  NOT_APPLICABLE: 'No SLA',
};

/** Shows a server-computed SLA status, optionally with the time left. It never recomputes the status. */
@Component({
  selector: 'app-sla-badge',
  template: `<span
    class="badge"
    [class]="'sla-' + status().toLowerCase().replace('_', '-')"
    [attr.title]="title()"
    >{{ label() }}
    @if (remainingText(); as r) {
      <span class="badge-detail"> · {{ r }}</span>
    }
  </span>`,
})
export class SlaBadge {
  readonly status = input.required<SlaStatus>();
  readonly remainingSeconds = input<number | null>(null);
  readonly showRemaining = input(false);

  protected readonly label = computed(() => SLA_LABELS[this.status()]);
  protected readonly remainingText = computed(() =>
    this.showRemaining() &&
    (this.status() === 'ON_TRACK' || this.status() === 'AT_RISK' || this.status() === 'BREACHED')
      ? formatRemaining(this.remainingSeconds())
      : null,
  );
  protected readonly title = computed(() => `SLA: ${this.label()}`);
}
