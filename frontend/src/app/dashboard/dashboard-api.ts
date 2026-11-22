import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Role } from '../auth/auth.model';
import { TicketCategory, TicketPriority, TicketStatus } from '../tickets/ticket.model';

export type WorkloadLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export interface AgentWorkload {
  userId: number;
  username: string;
  displayName: string;
  role: Role;
  activeTickets: number;
  urgentTickets: number;
  overdueTickets: number;
  level: WorkloadLevel;
}

export interface TeamWorkload {
  teamId: number;
  name: string;
  activeTickets: number;
  unassignedTickets: number;
  overdueTickets: number;
  level: WorkloadLevel;
}

export interface Workload {
  agents: AgentWorkload[];
  teams: TeamWorkload[];
  unassignedTickets: number;
}

export interface Dashboard {
  scope: 'all' | 'work-queue' | 'own';
  totals: {
    total: number;
    open: number;
    inProgress: number;
    waiting: number;
    resolved: number;
    closed: number;
    cancelled: number;
    highPriority: number;
    critical: number;
    unassigned: number;
    overdue: number;
  };
  byStatus: Record<TicketStatus, number>;
  byPriority: Record<TicketPriority, number>;
  byCategory: Record<TicketCategory, number>;
  sla: {
    responseCompliancePercent: number | null;
    responseMeasured: number;
    resolutionCompliancePercent: number | null;
    resolutionMeasured: number;
    responseBreachedActive: number;
    atRiskActive: number;
  };
  averageResolutionHours: number | null;
  trend: { date: string; created: number; resolved: number }[];
  workload: Workload | null;
}

@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);

  dashboard(trendDays = 14): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/dashboard', { params: { trendDays } });
  }
}
