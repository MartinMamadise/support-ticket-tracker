import {
  Activity,
  CATEGORY_LABELS,
  PRIORITY_LABELS,
  STATUS_LABELS,
  TicketCategory,
  TicketPriority,
  TicketStatus,
} from './ticket.model';

const status = (v: string | null) => (v ? (STATUS_LABELS[v as TicketStatus] ?? v) : '—');
const priority = (v: string | null) => (v ? (PRIORITY_LABELS[v as TicketPriority] ?? v) : '—');
const category = (v: string | null) => (v ? (CATEGORY_LABELS[v as TicketCategory] ?? v) : '—');

/** One readable sentence per activity entry (without the actor, which the timeline shows separately). */
export function describeActivity(a: Activity): string {
  switch (a.type) {
    case 'CREATED':
      return 'created the ticket';
    case 'UPDATED':
      return 'edited the title or description';
    case 'STATUS_CHANGED':
      return `changed the status from ${status(a.oldValue)} to ${status(a.newValue)}`;
    case 'RESOLVED':
      return 'resolved the ticket';
    case 'REOPENED':
      return 'reopened the ticket';
    case 'CLOSED':
      return 'closed the ticket';
    case 'CANCELLED':
      return 'cancelled the ticket';
    case 'PRIORITY_CHANGED':
      return `changed the priority from ${priority(a.oldValue)} to ${priority(a.newValue)}`;
    case 'CATEGORY_CHANGED':
      return `changed the category from ${category(a.oldValue)} to ${category(a.newValue)}`;
    case 'ASSIGNEE_CHANGED':
      return a.newValue
        ? `assigned the ticket to ${a.newValue}`
        : `removed the assignee (${a.oldValue ?? '—'})`;
    case 'TEAM_CHANGED':
      return a.newValue
        ? `moved the ticket to team ${a.newValue}`
        : `removed the team (${a.oldValue ?? '—'})`;
    case 'COMMENT_ADDED':
      return 'added a comment';
    case 'COMMENT_EDITED':
      return a.internal ? 'edited an internal note' : 'edited a comment';
    case 'COMMENT_DELETED':
      return a.internal ? 'deleted an internal note' : 'deleted a comment';
    case 'INTERNAL_NOTE_ADDED':
      return 'added an internal note';
    case 'ATTACHMENT_ADDED':
      return `attached ${a.newValue ?? 'a file'}`;
    case 'ATTACHMENT_REMOVED':
      return `removed the attachment ${a.oldValue ?? ''}`.trim();
    case 'SLA_RESPONSE_BREACHED':
      return 'Response SLA breached';
    case 'SLA_RESOLUTION_BREACHED':
      return 'Resolution SLA breached';
  }
}
