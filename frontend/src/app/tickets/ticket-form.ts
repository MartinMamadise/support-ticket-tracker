import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NotificationService } from '../shared/notification';
import { TicketApi, errorMessage, fieldErrors } from './ticket-api';
import {
  CATEGORY_LABELS,
  PRIORITY_LABELS,
  STATUS_LABELS,
  TICKET_CATEGORIES,
  TICKET_PRIORITIES,
  TICKET_STATUSES,
  TicketCategory,
  TicketRequest,
  TicketStatus,
} from './ticket.model';

type FieldName = 'title' | 'description' | 'status' | 'priority' | 'category';

/** Used for both "New ticket" (no id in the route) and "Edit ticket". */
@Component({
  selector: 'app-ticket-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './ticket-form.html',
})
export class TicketForm {
  private readonly api = inject(TicketApi);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly idParam = inject(ActivatedRoute).snapshot.paramMap.get('id');

  /** Statuses offered when editing: the current one plus the moves the backend allows. */
  protected readonly statuses = signal<TicketStatus[]>([...TICKET_STATUSES]);
  protected readonly priorities = TICKET_PRIORITIES;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly priorityLabels = PRIORITY_LABELS;
  protected readonly categories = TICKET_CATEGORIES;
  protected readonly categoryLabels = CATEGORY_LABELS;
  /** False when the backend says the current user may no longer change the details (e.g. requester, not OPEN). */
  protected readonly canEditDetails = signal(true);

  protected readonly ticketId = this.idParam === null ? null : Number(this.idParam);
  protected readonly isEdit = this.ticketId !== null;

  protected readonly loading = signal(this.isEdit);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly serverErrors = signal<Record<string, string>>({});
  protected readonly submitted = signal(false);
  /** Version of the ticket as loaded for editing; sent back so concurrent edits are detected. */
  private loadedVersion: number | undefined;

  protected readonly form = this.fb.group({
    title: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(150)]],
    description: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(2000)]],
    status: ['OPEN' as TicketRequest['status'], Validators.required],
    priority: ['MEDIUM' as TicketRequest['priority'], Validators.required],
    category: ['OTHER' as TicketCategory, Validators.required],
  });

  constructor() {
    if (this.ticketId !== null) {
      this.loadTicket(this.ticketId);
    }
  }

  private loadTicket(id: number): void {
    if (!Number.isInteger(id) || id <= 0) {
      this.loadError.set('Invalid ticket number.');
      this.loading.set(false);
      return;
    }
    this.api.get(id).subscribe({
      next: (ticket) => {
        this.loadedVersion = ticket.version;
        this.statuses.set([ticket.status, ...ticket.allowedTransitions]);
        this.form.patchValue({
          title: ticket.title,
          description: ticket.description,
          status: ticket.status,
          priority: ticket.priority,
          category: ticket.category ?? 'OTHER',
        });
        if (ticket.permissions && !ticket.permissions.canEdit) {
          this.canEditDetails.set(false);
        }
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  /** The message to show under a field, or null. Client rules first, then server rules. */
  protected errorFor(field: FieldName): string | null {
    const control = this.form.controls[field];
    if (control.invalid && (control.touched || this.submitted())) {
      const errors = control.errors ?? {};
      const label = field === 'title' ? 'Title' : 'Description';
      if (errors['required'] || errors['pattern']) {
        return `${label} is required.`;
      }
      if (errors['maxlength']) {
        const max = (errors['maxlength'] as { requiredLength: number }).requiredLength;
        return `${label} must be at most ${max} characters.`;
      }
      return 'This value is not valid.';
    }
    return this.serverErrors()[field] ?? null;
  }

  protected submit(): void {
    this.submitted.set(true);
    this.serverErrors.set({});
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const request: TicketRequest = {
      title: value.title.trim(),
      description: value.description.trim(),
      priority: value.priority,
      category: value.category,
      // The status is only sent when editing; the backend starts every new ticket as OPEN.
      ...(this.isEdit ? { status: value.status } : {}),
      ...(this.loadedVersion === undefined ? {} : { version: this.loadedVersion }),
    };

    this.saving.set(true);
    const call =
      this.ticketId === null ? this.api.create(request) : this.api.update(this.ticketId, request);

    call.subscribe({
      next: (ticket) => {
        this.notifications.success(
          this.isEdit ? `Ticket #${ticket.id} updated.` : `Ticket #${ticket.id} created.`,
        );
        void this.router.navigate(['/tickets', ticket.id]);
      },
      error: (err) => {
        this.saving.set(false);
        this.serverErrors.set(fieldErrors(err));
        this.notifications.error(errorMessage(err));
      },
    });
  }
}
