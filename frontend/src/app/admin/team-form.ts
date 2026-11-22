import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NotificationService } from '../shared/notification';
import { errorMessage } from '../tickets/ticket-api';
import { UserSummary } from '../tickets/ticket.model';
import { AdminApi, Team } from './admin-api';

/** Create (/admin/teams/new) or edit (/admin/teams/:id) a team and manage its members. */
@Component({
  selector: 'app-team-form',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <p class="breadcrumb"><a routerLink="/admin/teams">&larr; Teams</a></p>
    <div class="page-header">
      <h1>{{ team() ? team()!.name : 'New team' }}</h1>
    </div>
    @if (loadError()) {
      <div class="panel panel-error" role="alert">
        <p>{{ loadError() }}</p>
      </div>
    } @else {
      <form class="panel form" [formGroup]="form" (ngSubmit)="save()" novalidate>
        <div class="field">
          <label for="name">Name</label>
          <input id="name" formControlName="name" maxlength="80" />
          @if (form.controls.name.invalid && form.controls.name.touched) {
            <p class="field-error">Name must be 2–80 characters.</p>
          }
        </div>
        <div class="field">
          <label for="description">Description</label>
          <textarea
            id="description"
            rows="2"
            formControlName="description"
            maxlength="500"
          ></textarea>
        </div>
        <label class="checkbox"
          ><input type="checkbox" formControlName="active" /> Active (can receive tickets)</label
        >
        <div class="form-actions">
          <button type="submit" class="btn btn-primary" [disabled]="saving()">Save</button>
          <a class="btn" routerLink="/admin/teams">Cancel</a>
        </div>
      </form>

      @if (team(); as t) {
        <section class="panel">
          <h2>Members</h2>
          <p class="muted small">
            Only active administrators and support agents can be members.
            {{ t.activeTickets }} active tickets, {{ t.unassignedTickets }} without an agent.
          </p>
          <fieldset class="member-list">
            <legend class="visually-hidden">Team members</legend>
            @for (u of staff(); track u.id) {
              <label class="checkbox">
                <input type="checkbox" [checked]="selected().has(u.id)" (change)="toggle(u.id)" />
                {{ u.displayName }} <span class="muted">({{ u.username }})</span>
              </label>
            } @empty {
              <p class="muted">There are no active support staff accounts yet.</p>
            }
          </fieldset>
          <button type="button" class="btn" [disabled]="!membersChanged()" (click)="saveMembers()">
            Save members
          </button>
        </section>
      }
    }
  `,
})
export class TeamForm {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly idParam = inject(ActivatedRoute).snapshot.paramMap.get('id');
  private readonly id = this.idParam === null ? null : Number(this.idParam);

  protected readonly team = signal<Team | null>(null);
  protected readonly staff = signal<UserSummary[]>([]);
  protected readonly selected = signal<Set<number>>(new Set());
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly membersChanged = computed(() => {
    const current = new Set((this.team()?.members ?? []).map((m) => m.id));
    const chosen = this.selected();
    return current.size !== chosen.size || [...chosen].some((id) => !current.has(id));
  });

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(80)]],
    description: [''],
    active: [true],
  });

  constructor() {
    if (this.id !== null) {
      this.api.team(this.id).subscribe({
        next: (t) => this.applyTeam(t),
        error: (e) => this.loadError.set(errorMessage(e)),
      });
      this.api
        .assignable()
        .subscribe({ next: (s) => this.staff.set(s), error: () => this.staff.set([]) });
    }
  }

  private applyTeam(t: Team): void {
    this.team.set(t);
    this.selected.set(new Set(t.members.map((m) => m.id)));
    this.form.patchValue({ name: t.name, description: t.description ?? '', active: t.active });
  }

  protected toggle(id: number): void {
    this.selected.update((set) => {
      const next = new Set(set);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request = {
      name: v.name.trim(),
      description: v.description.trim() || null,
      active: v.active,
    };
    this.saving.set(true);
    const call =
      this.id === null ? this.api.createTeam(request) : this.api.updateTeam(this.id, request);
    call.subscribe({
      next: (t) => {
        this.saving.set(false);
        this.notifications.success(`Team ${t.name} saved.`);
        if (this.id === null) {
          void this.router.navigate(['/admin/teams', t.id]);
        } else {
          this.applyTeam(t);
        }
      },
      error: (e) => {
        this.saving.set(false);
        this.notifications.error(errorMessage(e));
      },
    });
  }

  protected saveMembers(): void {
    if (this.id === null) {
      return;
    }
    this.api.setTeamMembers(this.id, [...this.selected()]).subscribe({
      next: (t) => {
        this.applyTeam(t);
        this.notifications.success('Members saved.');
      },
      error: (e) => this.notifications.error(errorMessage(e)),
    });
  }
}
