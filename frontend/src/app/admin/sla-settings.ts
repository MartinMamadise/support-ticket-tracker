import { Component, inject, signal } from '@angular/core';
import { NotificationService } from '../shared/notification';
import { formatMinutes } from '../shared/format';
import { errorMessage } from '../tickets/ticket-api';
import { PRIORITY_LABELS } from '../tickets/ticket.model';
import { AdminApi, SlaPolicy } from './admin-api';

/** SLA targets per priority (calendar minutes). Changes apply to deadlines computed from now on. */
@Component({
  selector: 'app-sla-settings',
  template: `
    <div class="page-header">
      <div>
        <h1>SLA targets</h1>
        <p class="muted page-subtitle">
          Calendar time, 24/7. The resolution clock pauses while a ticket waits for the requester.
          Changes apply to new deadlines; existing deadlines are not rewritten.
        </p>
      </div>
    </div>
    @if (error()) {
      <div class="panel panel-error" role="alert">
        <p>{{ error() }}</p>
      </div>
    }
    <div class="table-wrap card card-flush">
      <table>
        <thead>
          <tr>
            <th scope="col">Priority</th>
            <th scope="col">First response (minutes)</th>
            <th scope="col">Resolution (minutes)</th>
            <th scope="col"><span class="visually-hidden">Save</span></th>
          </tr>
        </thead>
        <tbody>
          @for (p of policies(); track p.priority) {
            <tr>
              <th scope="row">{{ labels[p.priority] }}</th>
              <td>
                <input
                  type="number"
                  min="1"
                  [attr.aria-label]="labels[p.priority] + ' response minutes'"
                  #r
                  [value]="p.responseMinutes"
                />
                <span class="muted small">{{ fmt(p.responseMinutes) }}</span>
              </td>
              <td>
                <input
                  type="number"
                  min="1"
                  [attr.aria-label]="labels[p.priority] + ' resolution minutes'"
                  #s
                  [value]="p.resolutionMinutes"
                />
                <span class="muted small">{{ fmt(p.resolutionMinutes) }}</span>
              </td>
              <td>
                <button type="button" class="btn btn-sm" (click)="save(p, r.value, s.value)">
                  Save
                </button>
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class SlaSettings {
  private readonly api = inject(AdminApi);
  private readonly notifications = inject(NotificationService);
  protected readonly policies = signal<SlaPolicy[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly labels = PRIORITY_LABELS;
  protected readonly fmt = formatMinutes;

  constructor() {
    this.api.slaPolicies().subscribe({
      next: (p) => this.policies.set(p),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected save(policy: SlaPolicy, response: string, resolution: string): void {
    this.api.updateSlaPolicy(policy.priority, Number(response), Number(resolution)).subscribe({
      next: (updated) => {
        this.policies.update((list) =>
          list.map((p) => (p.priority === updated.priority ? updated : p)),
        );
        this.notifications.success(`${this.labels[updated.priority]} targets saved.`);
      },
      error: (e) => this.notifications.error(errorMessage(e)),
    });
  }
}
