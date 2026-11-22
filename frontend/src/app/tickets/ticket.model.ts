export const TICKET_STATUSES = [
  'OPEN',
  'IN_PROGRESS',
  'WAITING',
  'RESOLVED',
  'CLOSED',
  'CANCELLED',
] as const;
export const TICKET_PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;
export const TICKET_CATEGORIES = [
  'HARDWARE',
  'SOFTWARE',
  'NETWORK',
  'ACCESS',
  'EMAIL',
  'OTHER',
] as const;

export type TicketStatus = (typeof TICKET_STATUSES)[number];
export type TicketPriority = (typeof TICKET_PRIORITIES)[number];
export type TicketCategory = (typeof TICKET_CATEGORIES)[number];

export const STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  WAITING: 'Waiting on requester',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
  CANCELLED: 'Cancelled',
};

export const PRIORITY_LABELS: Record<TicketPriority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
};

export const CATEGORY_LABELS: Record<TicketCategory, string> = {
  HARDWARE: 'Hardware',
  SOFTWARE: 'Software',
  NETWORK: 'Network',
  ACCESS: 'Access & accounts',
  EMAIL: 'Email',
  OTHER: 'Other',
};

/** Verb shown on the button that moves a ticket to this status. */
export const STATUS_ACTIONS: Record<TicketStatus, string> = {
  OPEN: 'Reopen',
  IN_PROGRESS: 'Start work',
  WAITING: 'Wait for requester',
  RESOLVED: 'Resolve',
  CLOSED: 'Close',
  CANCELLED: 'Cancel ticket',
};

/** Short reference to an account (never contains an email address). */
export interface UserSummary {
  id: number;
  username: string;
  displayName: string;
}

export interface TeamSummary {
  id: number;
  name: string;
}

export type SlaStatus = 'ON_TRACK' | 'AT_RISK' | 'BREACHED' | 'MET' | 'PAUSED' | 'NOT_APPLICABLE';

/** SLA state as computed by the server; the UI only displays it. */
export interface SlaInfo {
  firstResponseAt: string | null;
  responseDueAt: string | null;
  resolutionDueAt: string | null;
  responseStatus: SlaStatus;
  resolutionStatus: SlaStatus;
  responseRemainingSeconds: number | null;
  resolutionRemainingSeconds: number | null;
  paused: boolean;
  evaluatedAt: string;
}

/** What the current user may do with a ticket (informational: the server checks again). */
export interface TicketPermissions {
  canEdit: boolean;
  canChangeStatus: boolean;
  canAssign: boolean;
  canComment: boolean;
  canAddInternalNote: boolean;
  canViewInternal: boolean;
  canUpload: boolean;
  canDelete: boolean;
}

/** A ticket as returned by the API. */
export interface Ticket {
  id: number;
  title: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  category: TicketCategory;
  requester: UserSummary | null;
  assignee: UserSummary | null;
  team: TeamSummary | null;
  /** ISO-8601 UTC instants, e.g. 2026-11-21T12:38:00.123Z. */
  createdAt: string;
  updatedAt: string;
  /** Set when the ticket was first resolved or closed; null while it is active. */
  resolvedAt: string | null;
  /** Optimistic-locking counter; send it back on update to detect concurrent edits. */
  version: number;
  /** Statuses the current user may move this ticket to next. */
  allowedTransitions: TicketStatus[];
  sla: SlaInfo | null;
  permissions: TicketPermissions | null;
}

/** One page of results as returned by the paged endpoints. */
export interface Page<T> {
  content: T[];
  /** Zero-based page number. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export const TICKET_SORT_FIELDS = [
  'id',
  'createdAt',
  'updatedAt',
  'priority',
  'status',
  'category',
  'resolutionDueAt',
] as const;
export type TicketSortField = (typeof TICKET_SORT_FIELDS)[number];

export const TICKET_VIEWS = ['all', 'mine', 'assigned', 'team', 'unassigned'] as const;
export type TicketView = (typeof TICKET_VIEWS)[number];

/** Optional query parameters of GET /api/tickets; anything left out uses the server default. */
export interface TicketListParams {
  page?: number;
  size?: number;
  sortBy?: TicketSortField;
  direction?: 'asc' | 'desc';
  status?: TicketStatus[];
  priority?: TicketPriority[];
  category?: TicketCategory[];
  view?: TicketView;
  teamId?: number;
  assigneeId?: number;
  requesterId?: number;
  /** yyyy-MM-dd, UTC, inclusive. */
  createdFrom?: string;
  createdTo?: string;
  /** Text in the title or description, or a ticket number. */
  q?: string;
}

/** Body sent when creating or updating a ticket. */
export interface TicketRequest {
  title: string;
  description: string;
  /** Omit when creating: new tickets always start OPEN. */
  status?: TicketStatus;
  priority: TicketPriority;
  category?: TicketCategory;
  /** The ticket's version as last loaded (update only). The API answers 409 if it is stale. */
  version?: number;
}

export interface Comment {
  id: number;
  ticketId: number;
  author: UserSummary;
  /** Plain text. Always rendered with interpolation, never as HTML. */
  body: string;
  internal: boolean;
  edited: boolean;
  createdAt: string;
  updatedAt: string;
  canEdit: boolean;
}

export type ActivityType =
  | 'CREATED'
  | 'UPDATED'
  | 'STATUS_CHANGED'
  | 'RESOLVED'
  | 'REOPENED'
  | 'CLOSED'
  | 'CANCELLED'
  | 'PRIORITY_CHANGED'
  | 'CATEGORY_CHANGED'
  | 'ASSIGNEE_CHANGED'
  | 'TEAM_CHANGED'
  | 'COMMENT_ADDED'
  | 'COMMENT_EDITED'
  | 'COMMENT_DELETED'
  | 'INTERNAL_NOTE_ADDED'
  | 'ATTACHMENT_ADDED'
  | 'ATTACHMENT_REMOVED'
  | 'SLA_RESPONSE_BREACHED'
  | 'SLA_RESOLUTION_BREACHED';

export interface Activity {
  id: number;
  type: ActivityType;
  actor: UserSummary | null;
  entityType: 'TICKET' | 'COMMENT' | 'ATTACHMENT';
  entityId: number | null;
  field: string | null;
  oldValue: string | null;
  newValue: string | null;
  internal: boolean;
  createdAt: string;
}

export interface Attachment {
  id: number;
  ticketId: number;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploadedBy: UserSummary;
  createdAt: string;
  downloadUrl: string;
  canDelete: boolean;
}

export interface AssignmentEntry {
  id: number;
  previousAssignee: UserSummary | null;
  newAssignee: UserSummary | null;
  previousTeam: TeamSummary | null;
  newTeam: TeamSummary | null;
  actor: UserSummary | null;
  createdAt: string;
}

/** Error body returned by the Spring Boot API. */
export interface ApiError {
  status: number;
  error: string;
  message: string;
  fieldErrors?: Record<string, string>;
}
