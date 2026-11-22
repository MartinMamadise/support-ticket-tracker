import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthUser, ROLE_LABELS, Role } from '../auth/auth.model';
import { errorMessage } from '../tickets/ticket-api';
import { Page } from '../tickets/ticket.model';
import { AdminApi } from './admin-api';

@Component({
  selector: 'app-user-list',
  imports: [RouterLink, DatePipe],
  template: `
    <div class="page-header">
      <h1>Users</h1>
      <a routerLink="/admin/users/new" class="btn btn-primary">New user</a>
    </div>
    <div class="toolbar card">
      <form class="field search-field" (submit)="search($event)">
        <label for="user-search">Search</label>
        <div class="search-row">
          <input
            id="user-search"
            type="search"
            placeholder="Username, email or name"
            #q
            [value]="query()"
            (input)="query.set(q.value)"
          />
          <button type="submit" class="btn">Search</button>
        </div>
      </form>
      <div class="field">
        <label for="role-filter">Role</label>
        <select id="role-filter" #r (change)="setRole(r.value)">
          <option value="">All roles</option>
          @for (role of roles; track role) {
            <option [value]="role">{{ roleLabels[role] }}</option>
          }
        </select>
      </div>
      <div class="field">
        <label for="status-filter">Status</label>
        <select id="status-filter" #s (change)="setEnabled(s.value)">
          <option value="">Active and inactive</option>
          <option value="true">Active</option>
          <option value="false">Deactivated</option>
        </select>
      </div>
    </div>
    @if (error()) {
      <div class="panel panel-error" role="alert">
        <p>{{ error() }}</p>
        <button type="button" class="btn" (click)="load()">Try again</button>
      </div>
    } @else if (page(); as p) {
      <div class="table-wrap card card-flush">
        <table>
          <thead>
            <tr>
              <th scope="col">Username</th>
              <th scope="col">Name</th>
              <th scope="col">Email</th>
              <th scope="col">Role</th>
              <th scope="col">Status</th>
              <th scope="col">Created</th>
            </tr>
          </thead>
          <tbody>
            @for (u of p.content; track u.id) {
              <tr>
                <td>
                  <a [routerLink]="['/admin/users', u.id]">{{ u.username }}</a>
                </td>
                <td>{{ u.fullName ?? '—' }}</td>
                <td>{{ u.email }}</td>
                <td>{{ roleLabels[u.role] }}</td>
                <td>
                  @if (u.enabled) {
                    <span class="badge status-resolved">Active</span>
                  } @else {
                    <span class="badge status-cancelled">Deactivated</span>
                  }
                </td>
                <td class="nowrap">{{ u.createdAt | date: 'dd MMM yyyy' }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="6" class="muted empty-row">No accounts match.</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      @if (p.totalPages > 1) {
        <nav class="pager" aria-label="User pages">
          <button type="button" class="btn btn-sm" [disabled]="!p.hasPrevious" (click)="go(-1)">
            Previous
          </button>
          <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages }}</span>
          <button type="button" class="btn btn-sm" [disabled]="!p.hasNext" (click)="go(1)">
            Next
          </button>
        </nav>
      }
    } @else {
      <p class="muted" aria-busy="true">Loading users…</p>
    }
  `,
})
export class UserList {
  private readonly api = inject(AdminApi);
  protected readonly roles: Role[] = ['ADMIN', 'SUPPORT_AGENT', 'USER'];
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly page = signal<Page<AuthUser> | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal('');
  private readonly role = signal<Role | ''>('');
  private readonly enabled = signal<'' | 'true' | 'false'>('');
  private readonly pageNumber = signal(0);
  protected readonly hasData = computed(() => this.page() !== null);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.api
      .users({
        q: this.query().trim() || undefined,
        role: this.role() || undefined,
        enabled: this.enabled() === '' ? undefined : this.enabled() === 'true',
        page: this.pageNumber(),
      })
      .subscribe({ next: (p) => this.page.set(p), error: (e) => this.error.set(errorMessage(e)) });
  }

  protected search(event: Event): void {
    event.preventDefault();
    this.pageNumber.set(0);
    this.load();
  }

  protected setRole(value: string): void {
    this.role.set(value as Role | '');
    this.pageNumber.set(0);
    this.load();
  }

  protected setEnabled(value: string): void {
    this.enabled.set(value as '' | 'true' | 'false');
    this.pageNumber.set(0);
    this.load();
  }

  protected go(delta: number): void {
    this.pageNumber.update((p) => Math.max(0, p + delta));
    this.load();
  }
}
