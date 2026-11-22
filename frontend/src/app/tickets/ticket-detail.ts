import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AdminApi, Team } from '../admin/admin-api';
import { AuthService } from '../auth/auth.service';
import { ConfirmService } from '../shared/confirm';
import { formatBytes, formatRemaining } from '../shared/format';
import { NotificationService } from '../shared/notification';
import { SlaBadge } from '../shared/sla-badge';
import { TicketBadge } from '../shared/ticket-badge';
import { describeActivity } from './activity-text';
import { TicketApi, errorMessage } from './ticket-api';
import {
  Activity,
  Attachment,
  CATEGORY_LABELS,
  Comment,
  STATUS_ACTIONS,
  Ticket,
  TicketStatus,
  UserSummary,
} from './ticket.model';

/** Client-side pre-check only; the server validates type, content and size again. */
export const ALLOWED_UPLOAD_EXTENSIONS = ['png', 'jpg', 'jpeg', 'pdf', 'txt', 'log'];
export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

@Component({
  selector: 'app-ticket-detail',
  imports: [RouterLink, DatePipe, TicketBadge, SlaBadge],
  templateUrl: './ticket-detail.html',
})
export class TicketDetail {
  private readonly api = inject(TicketApi);
  private readonly adminApi = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmService);
  protected readonly auth = inject(AuthService);

  protected readonly ticketId = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));
  protected readonly ticket = signal<Ticket | null>(null);
  protected readonly comments = signal<Comment[]>([]);
  protected readonly activity = signal<Activity[]>([]);
  protected readonly attachments = signal<Attachment[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly categoryLabels = CATEGORY_LABELS;
  protected readonly statusActions = STATUS_ACTIONS;
  protected readonly describe = describeActivity;
  protected readonly formatBytes = formatBytes;
  protected readonly formatRemaining = formatRemaining;

  // Composer
  protected readonly commentText = signal('');
  protected readonly commentInternal = signal(false);
  protected readonly posting = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly editText = signal('');

  // Assignment (staff)
  protected readonly teams = signal<Team[]>([]);
  protected readonly assignable = signal<UserSummary[]>([]);
  protected readonly assignTeamId = signal<number | null>(null);
  protected readonly assignAgentId = signal<number | null>(null);

  // Attachments
  protected readonly uploading = signal(false);
  protected readonly uploadError = signal<string | null>(null);

  protected readonly permissions = computed(() => this.ticket()?.permissions ?? null);
  /** Primary action first (the "forward" move), destructive ones last. */
  protected readonly actions = computed<TicketStatus[]>(() => {
    const order: TicketStatus[] = [
      'IN_PROGRESS',
      'RESOLVED',
      'WAITING',
      'CLOSED',
      'OPEN',
      'CANCELLED',
    ];
    const allowed = this.ticket()?.allowedTransitions ?? [];
    return order.filter((s) => allowed.includes(s));
  });
  protected readonly assignmentChanged = computed(() => {
    const t = this.ticket();
    return (
      !!t &&
      (this.assignTeamId() !== (t.team?.id ?? null) ||
        this.assignAgentId() !== (t.assignee?.id ?? null))
    );
  });

  constructor() {
    if (!Number.isInteger(this.ticketId) || this.ticketId <= 0) {
      this.loadError.set('Invalid ticket number.');
      this.loading.set(false);
      return;
    }
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    forkJoin({
      ticket: this.api.get(this.ticketId),
      comments: this.api.comments(this.ticketId),
      activity: this.api.activity(this.ticketId),
      attachments: this.api.attachments(this.ticketId),
    }).subscribe({
      next: (r) => {
        this.applyTicket(r.ticket);
        this.comments.set(r.comments);
        this.activity.set(r.activity);
        this.attachments.set(r.attachments);
        this.loading.set(false);
        if (r.ticket.permissions?.canAssign && this.teams().length === 0) {
          this.loadAssignmentOptions();
        }
      },
      error: (err) => {
        this.loadError.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  private applyTicket(ticket: Ticket): void {
    this.ticket.set(ticket);
    this.assignTeamId.set(ticket.team?.id ?? null);
    this.assignAgentId.set(ticket.assignee?.id ?? null);
  }

  /** Refreshes the ticket header and the timeline after a change. */
  private refreshAfterChange(ticket?: Ticket): void {
    if (ticket) {
      this.applyTicket(ticket);
    }
    this.api
      .activity(this.ticketId)
      .subscribe({ next: (a) => this.activity.set(a), error: () => undefined });
  }

  private loadAssignmentOptions(): void {
    this.adminApi
      .teams()
      .subscribe({ next: (t) => this.teams.set(t), error: () => this.teams.set([]) });
    this.loadAgents(this.assignTeamId());
  }

  private loadAgents(teamId: number | null): void {
    this.adminApi
      .assignable(teamId)
      .subscribe({ next: (a) => this.assignable.set(a), error: () => this.assignable.set([]) });
  }

  // ---- status ----

  protected async changeStatus(target: TicketStatus): Promise<void> {
    const ticket = this.ticket();
    if (!ticket || this.busy()) {
      return;
    }
    if (target === 'CANCELLED' || target === 'CLOSED') {
      const ok = await this.confirm.ask({
        title: target === 'CANCELLED' ? 'Cancel this ticket?' : 'Close this ticket?',
        message: 'Closed and cancelled tickets cannot be changed or reopened afterwards.',
        confirmLabel: target === 'CANCELLED' ? 'Cancel ticket' : 'Close ticket',
        danger: target === 'CANCELLED',
      });
      if (!ok) {
        return;
      }
    }
    this.busy.set(true);
    this.api.changeStatus(ticket.id, target, ticket.version).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.notifications.success(
          `Ticket #${updated.id} is now ${updated.status.replace('_', ' ').toLowerCase()}.`,
        );
        this.refreshAfterChange(updated);
      },
      error: (err) => {
        this.busy.set(false);
        this.notifications.error(errorMessage(err));
        this.load();
      },
    });
  }

  // ---- assignment ----

  protected setAssignTeam(value: string): void {
    const teamId = value ? Number(value) : null;
    this.assignTeamId.set(teamId);
    this.loadAgents(teamId);
    // The agent must belong to the new team; let the user pick again.
    this.assignAgentId.set(null);
  }

  protected setAssignAgent(value: string): void {
    this.assignAgentId.set(value ? Number(value) : null);
  }

  protected assignToMe(): void {
    const me = this.auth.user();
    if (me) {
      this.assignAgentId.set(me.id);
      this.saveAssignment();
    }
  }

  protected saveAssignment(): void {
    const ticket = this.ticket();
    if (!ticket || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api
      .assign(ticket.id, this.assignAgentId(), this.assignTeamId(), ticket.version)
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.notifications.success('Assignment saved.');
          this.refreshAfterChange(updated);
        },
        error: (err) => {
          this.busy.set(false);
          this.notifications.error(errorMessage(err));
        },
      });
  }

  // ---- comments ----

  protected postComment(event: Event): void {
    event.preventDefault();
    const body = this.commentText().trim();
    if (!body || this.posting()) {
      return;
    }
    this.posting.set(true);
    this.api.addComment(this.ticketId, body, this.commentInternal()).subscribe({
      next: (comment) => {
        this.posting.set(false);
        this.comments.update((list) => [...list, comment]);
        this.commentText.set('');
        this.commentInternal.set(false);
        // A reply can change the ticket (first response, WAITING -> IN_PROGRESS).
        this.api
          .get(this.ticketId)
          .subscribe({ next: (t) => this.refreshAfterChange(t), error: () => undefined });
      },
      error: (err) => {
        this.posting.set(false);
        this.notifications.error(errorMessage(err));
      },
    });
  }

  protected startEdit(comment: Comment): void {
    this.editingId.set(comment.id);
    this.editText.set(comment.body);
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
  }

  protected saveEdit(comment: Comment): void {
    const body = this.editText().trim();
    if (!body) {
      return;
    }
    this.api.editComment(this.ticketId, comment.id, body).subscribe({
      next: (updated) => {
        this.comments.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
        this.editingId.set(null);
      },
      error: (err) => this.notifications.error(errorMessage(err)),
    });
  }

  protected async deleteComment(comment: Comment): Promise<void> {
    const ok = await this.confirm.ask({
      title: comment.internal ? 'Delete internal note?' : 'Delete comment?',
      message: 'This cannot be undone.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.api.deleteComment(this.ticketId, comment.id).subscribe({
      next: () => {
        this.comments.update((list) => list.filter((c) => c.id !== comment.id));
        this.refreshAfterChange();
      },
      error: (err) => this.notifications.error(errorMessage(err)),
    });
  }

  // ---- attachments ----

  protected upload(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    this.uploadError.set(null);
    if (!file) {
      return;
    }
    const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
    if (!ALLOWED_UPLOAD_EXTENSIONS.includes(extension)) {
      this.uploadError.set('Allowed file types: PNG, JPG, PDF, TXT and LOG.');
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      this.uploadError.set('Files can be at most 5 MB.');
      return;
    }
    this.uploading.set(true);
    this.api.upload(this.ticketId, file).subscribe({
      next: (attachment) => {
        this.uploading.set(false);
        this.attachments.update((list) => [...list, attachment]);
        this.refreshAfterChange();
      },
      error: (err) => {
        this.uploading.set(false);
        this.uploadError.set(errorMessage(err));
      },
    });
  }

  protected async deleteAttachment(attachment: Attachment): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Remove attachment?',
      message: `"${attachment.filename}" will be deleted.`,
      confirmLabel: 'Remove',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.api.deleteAttachment(this.ticketId, attachment.id).subscribe({
      next: () => {
        this.attachments.update((list) => list.filter((a) => a.id !== attachment.id));
        this.refreshAfterChange();
      },
      error: (err) => this.notifications.error(errorMessage(err)),
    });
  }

  // ---- delete ticket ----

  protected async deleteTicket(): Promise<void> {
    const ok = await this.confirm.ask({
      title: `Delete ticket #${this.ticketId}?`,
      message: 'The ticket, its comments, history and attachments are deleted permanently.',
      confirmLabel: 'Delete ticket',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.api.delete(this.ticketId).subscribe({
      next: () => {
        this.notifications.success(`Ticket #${this.ticketId} deleted.`);
        void this.router.navigate(['/tickets']);
      },
      error: (err) => this.notifications.error(errorMessage(err)),
    });
  }
}
