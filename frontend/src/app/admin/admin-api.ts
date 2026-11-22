import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthUser, Role } from '../auth/auth.model';
import { toParams } from '../tickets/ticket-api';
import { Page, TicketPriority, UserSummary } from '../tickets/ticket.model';

export interface UserListParams {
  q?: string;
  role?: Role;
  enabled?: boolean;
  page?: number;
  size?: number;
}

export interface UserCreateRequest {
  username: string;
  email: string;
  fullName?: string | null;
  password: string;
  role: Role;
}

export interface UserUpdateRequest {
  email: string;
  fullName?: string | null;
  role: Role;
  enabled: boolean;
}

export interface Team {
  id: number;
  name: string;
  description: string | null;
  active: boolean;
  members: UserSummary[];
  activeTickets: number;
  unassignedTickets: number;
  createdAt: string;
  updatedAt: string;
}

export interface TeamRequest {
  name: string;
  description?: string | null;
  active?: boolean;
}

export interface SlaPolicy {
  priority: TicketPriority;
  responseMinutes: number;
  resolutionMinutes: number;
  updatedAt: string | null;
}

/** Administrator endpoints (/api/admin/**) plus the staff lookups used for assignment. */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  users(params: UserListParams = {}): Observable<Page<AuthUser>> {
    return this.http.get<Page<AuthUser>>('/api/admin/users', { params: toParams(params) });
  }

  user(id: number): Observable<AuthUser> {
    return this.http.get<AuthUser>(`/api/admin/users/${id}`);
  }

  createUser(request: UserCreateRequest): Observable<AuthUser> {
    return this.http.post<AuthUser>('/api/admin/users', request);
  }

  updateUser(id: number, request: UserUpdateRequest): Observable<AuthUser> {
    return this.http.put<AuthUser>(`/api/admin/users/${id}`, request);
  }

  resetPassword(id: number, newPassword: string): Observable<void> {
    return this.http.put<void>(`/api/admin/users/${id}/password`, { newPassword });
  }

  /** Staff: teams for pickers and queues. */
  teams(includeInactive = false): Observable<Team[]> {
    return this.http.get<Team[]>('/api/teams', { params: toParams({ includeInactive }) });
  }

  team(id: number): Observable<Team> {
    return this.http.get<Team>(`/api/teams/${id}`);
  }

  createTeam(request: TeamRequest): Observable<Team> {
    return this.http.post<Team>('/api/admin/teams', request);
  }

  updateTeam(id: number, request: TeamRequest): Observable<Team> {
    return this.http.put<Team>(`/api/admin/teams/${id}`, request);
  }

  setTeamMembers(id: number, userIds: number[]): Observable<Team> {
    return this.http.put<Team>(`/api/admin/teams/${id}/members`, { userIds });
  }

  /** Staff: accounts that can be assigned tickets (optionally members of one team). */
  assignable(teamId?: number | null): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>('/api/users/assignable', {
      params: toParams({ teamId: teamId ?? undefined }),
    });
  }

  slaPolicies(): Observable<SlaPolicy[]> {
    return this.http.get<SlaPolicy[]>('/api/admin/sla-policies');
  }

  updateSlaPolicy(
    priority: TicketPriority,
    responseMinutes: number,
    resolutionMinutes: number,
  ): Observable<SlaPolicy> {
    return this.http.put<SlaPolicy>(`/api/admin/sla-policies/${priority}`, {
      responseMinutes,
      resolutionMinutes,
    });
  }
}
