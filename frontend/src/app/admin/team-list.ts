import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../tickets/ticket-api';
import { AdminApi, Team } from './admin-api';

@Component({
  selector: 'app-team-list',
  imports: [RouterLink],
  template: `
    <div class="page-header">
      <h1>Teams</h1>
      <a routerLink="/admin/teams/new" class="btn btn-primary">New team</a>
    </div>
    @if (error()) {
      <div class="panel panel-error" role="alert">
        <p>{{ error() }}</p>
      </div>
    } @else if (teams(); as list) {
      <div class="table-wrap card card-flush">
        <table>
          <thead>
            <tr>
              <th scope="col">Team</th>
              <th scope="col">Members</th>
              <th scope="col">Active tickets</th>
              <th scope="col">Unassigned</th>
              <th scope="col">Status</th>
            </tr>
          </thead>
          <tbody>
            @for (t of list; track t.id) {
              <tr>
                <td>
                  <a [routerLink]="['/admin/teams', t.id]">{{ t.name }}</a>
                  @if (t.description) {
                    <span class="cell-sub">{{ t.description }}</span>
                  }
                </td>
                <td>{{ t.members.length }}</td>
                <td>{{ t.activeTickets }}</td>
                <td>{{ t.unassignedTickets }}</td>
                <td>
                  @if (t.active) {
                    <span class="badge status-resolved">Active</span>
                  } @else {
                    <span class="badge status-cancelled">Inactive</span>
                  }
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="muted empty-row">
                  No teams yet. Create one to route tickets to a group of agents.
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    } @else {
      <p class="muted" aria-busy="true">Loading teams…</p>
    }
  `,
})
export class TeamList {
  protected readonly teams = signal<Team[] | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    inject(AdminApi)
      .teams(true)
      .subscribe({
        next: (t) => this.teams.set(t),
        error: (e) => this.error.set(errorMessage(e)),
      });
  }
}
