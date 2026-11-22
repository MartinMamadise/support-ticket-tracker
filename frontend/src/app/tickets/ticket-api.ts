import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Activity,
  ApiError,
  AssignmentEntry,
  Attachment,
  Comment,
  Page,
  Ticket,
  TicketListParams,
  TicketRequest,
  TicketStatus,
} from './ticket.model';

/** Builds query parameters, leaving out empty values and repeating array values. */
export function toParams(params: object): HttpParams {
  let query = new HttpParams();
  for (const [name, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    if (Array.isArray(value)) {
      for (const item of value) {
        query = query.append(name, String(item));
      }
    } else {
      query = query.set(name, String(value));
    }
  }
  return query;
}

/** Thin wrapper around the ticket endpoints of the Spring Boot REST API. */
@Injectable({ providedIn: 'root' })
export class TicketApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/tickets';

  /** One page of tickets; filtering, search, sorting and paging are done by the server. */
  list(params: TicketListParams = {}): Observable<Page<Ticket>> {
    return this.http.get<Page<Ticket>>(this.baseUrl, { params: toParams(params) });
  }

  get(id: number): Observable<Ticket> {
    return this.http.get<Ticket>(`${this.baseUrl}/${id}`);
  }

  create(request: TicketRequest): Observable<Ticket> {
    return this.http.post<Ticket>(this.baseUrl, request);
  }

  update(id: number, request: TicketRequest): Observable<Ticket> {
    return this.http.put<Ticket>(`${this.baseUrl}/${id}`, request);
  }

  changeStatus(id: number, status: TicketStatus, version?: number): Observable<Ticket> {
    return this.http.put<Ticket>(`${this.baseUrl}/${id}/status`, { status, version });
  }

  assign(
    id: number,
    assigneeId: number | null,
    teamId: number | null,
    version?: number,
  ): Observable<Ticket> {
    return this.http.put<Ticket>(`${this.baseUrl}/${id}/assignment`, {
      assigneeId,
      teamId,
      version,
    });
  }

  assignments(id: number): Observable<AssignmentEntry[]> {
    return this.http.get<AssignmentEntry[]>(`${this.baseUrl}/${id}/assignments`);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  comments(id: number): Observable<Comment[]> {
    return this.http.get<Comment[]>(`${this.baseUrl}/${id}/comments`);
  }

  addComment(id: number, body: string, internal: boolean): Observable<Comment> {
    return this.http.post<Comment>(`${this.baseUrl}/${id}/comments`, { body, internal });
  }

  editComment(id: number, commentId: number, body: string): Observable<Comment> {
    return this.http.put<Comment>(`${this.baseUrl}/${id}/comments/${commentId}`, { body });
  }

  deleteComment(id: number, commentId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}/comments/${commentId}`);
  }

  activity(id: number): Observable<Activity[]> {
    return this.http.get<Activity[]>(`${this.baseUrl}/${id}/activity`);
  }

  attachments(id: number): Observable<Attachment[]> {
    return this.http.get<Attachment[]>(`${this.baseUrl}/${id}/attachments`);
  }

  upload(id: number, file: File): Observable<Attachment> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<Attachment>(`${this.baseUrl}/${id}/attachments`, form);
  }

  deleteAttachment(id: number, attachmentId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}/attachments/${attachmentId}`);
  }
}

/** Turns any HTTP failure into a message that is safe to show to the user. */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Cannot reach the server. Check that the backend is running.';
    }
    const body = error.error as Partial<ApiError> | null;
    if (body && typeof body.message === 'string') {
      return body.message;
    }
    return `Request failed (${error.status}).`;
  }
  return 'Something went wrong.';
}

/** Field-level validation messages from the API, if any. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiError> | null;
    return body?.fieldErrors ?? {};
  }
  return {};
}
