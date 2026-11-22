import { AuthUser } from '../auth/auth.model';
import { Ticket, TicketPermissions } from '../tickets/ticket.model';

export const ALL_PERMISSIONS: TicketPermissions = {
  canEdit: true,
  canChangeStatus: true,
  canAssign: true,
  canComment: true,
  canAddInternalNote: true,
  canViewInternal: true,
  canUpload: true,
  canDelete: true,
};

export const REQUESTER_PERMISSIONS: TicketPermissions = {
  canEdit: true,
  canChangeStatus: true,
  canAssign: false,
  canComment: true,
  canAddInternalNote: false,
  canViewInternal: false,
  canUpload: true,
  canDelete: false,
};

/** A complete ticket as the API returns it; override what a test cares about. */
export function makeTicket(overrides: Partial<Ticket> = {}): Ticket {
  return {
    id: 1,
    title: 'Printer offline',
    description: 'Nothing prints',
    status: 'OPEN',
    priority: 'MEDIUM',
    category: 'HARDWARE',
    requester: { id: 3, username: 'una.user', displayName: 'Una User' },
    assignee: null,
    team: null,
    createdAt: '2026-11-21T08:00:00Z',
    updatedAt: '2026-11-21T09:00:00Z',
    resolvedAt: null,
    version: 0,
    allowedTransitions: ['IN_PROGRESS', 'CANCELLED'],
    sla: {
      firstResponseAt: null,
      responseDueAt: '2026-11-21T12:00:00Z',
      resolutionDueAt: '2026-11-22T08:00:00Z',
      responseStatus: 'ON_TRACK',
      resolutionStatus: 'ON_TRACK',
      responseRemainingSeconds: 3600,
      resolutionRemainingSeconds: 72000,
      paused: false,
      evaluatedAt: '2026-11-21T11:00:00Z',
    },
    permissions: ALL_PERMISSIONS,
    ...overrides,
  };
}

export const ADMIN_USER: AuthUser = {
  id: 1,
  username: 'ada.admin',
  email: 'ada@example.com',
  role: 'ADMIN',
};
export const AGENT_USER: AuthUser = {
  id: 2,
  username: 'sam.agent',
  email: 'sam@example.com',
  role: 'SUPPORT_AGENT',
};
export const PLAIN_USER: AuthUser = {
  id: 3,
  username: 'una.user',
  email: 'una@example.com',
  role: 'USER',
};
