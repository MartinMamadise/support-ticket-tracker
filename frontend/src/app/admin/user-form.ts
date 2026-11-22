import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthUser, ROLE_LABELS, Role } from '../auth/auth.model';
import { AuthService } from '../auth/auth.service';
import { ConfirmService } from '../shared/confirm';
import { NotificationService } from '../shared/notification';
import { errorMessage, fieldErrors } from '../tickets/ticket-api';
import { AdminApi } from './admin-api';

/** Create (/admin/users/new) and edit (/admin/users/:id) an account, including password reset. */
@Component({
  selector: 'app-user-form',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <p class="breadcrumb"><a routerLink="/admin/users">&larr; Users</a></p>
    <div class="page-header">
      <h1>{{ isEdit ? 'Edit ' + (loaded()?.username ?? 'user') : 'New user' }}</h1>
    </div>
    @if (loadError()) {
      <div class="panel panel-error" role="alert">
        <p>{{ loadError() }}</p>
      </div>
    } @else {
      <form class="panel form" [formGroup]="form" (ngSubmit)="save()" novalidate>
        @if (!isEdit) {
          <div class="field">
            <label for="username">Username</label>
            <input id="username" formControlName="username" autocomplete="off" />
            <p class="field-hint">
              3–50 characters: letters, digits, ".", "_" or "-". Cannot be changed later.
            </p>
            @if (error('username'); as m) {
              <p class="field-error">{{ m }}</p>
            }
          </div>
        }
        <div class="field">
          <label for="fullName">Full name</label>
          <input id="fullName" formControlName="fullName" maxlength="100" />
          @if (error('fullName'); as m) {
            <p class="field-error">{{ m }}</p>
          }
        </div>
        <div class="field">
          <label for="email">Email</label>
          <input id="email" type="email" formControlName="email" autocomplete="off" />
          @if (error('email'); as m) {
            <p class="field-error">{{ m }}</p>
          }
        </div>
        <div class="field-row">
          <div class="field">
            <label for="role">Role</label>
            <select id="role" formControlName="role">
              @for (r of roles; track r) {
                <option [value]="r">{{ roleLabels[r] }}</option>
              }
            </select>
            @if (isSelf()) {
              <p class="field-hint">You cannot change your own role.</p>
            }
          </div>
          @if (isEdit) {
            <div class="field">
              <label class="checkbox"
                ><input type="checkbox" formControlName="enabled" /> Account active</label
              >
              <p class="field-hint">
                Deactivated accounts cannot sign in and are removed from their active tickets.
              </p>
            </div>
          }
        </div>
        @if (!isEdit) {
          <div class="field">
            <label for="password">Initial password</label>
            <input
              id="password"
              type="password"
              formControlName="password"
              autocomplete="new-password"
            />
            <p class="field-hint">At least 10 characters with a letter and a digit.</p>
            @if (error('password'); as m) {
              <p class="field-error">{{ m }}</p>
            }
          </div>
        }
        <div class="form-actions">
          <button type="submit" class="btn btn-primary" [disabled]="saving()">
            {{ saving() ? 'Saving…' : 'Save' }}
          </button>
          <a class="btn" routerLink="/admin/users">Cancel</a>
        </div>
      </form>

      @if (isEdit) {
        <form class="panel form" (submit)="resetPassword($event)">
          <h2>Set a new password</h2>
          <div class="field">
            <label for="newPassword">New password</label>
            <input
              id="newPassword"
              type="password"
              autocomplete="new-password"
              #pw
              [value]="newPassword()"
              (input)="newPassword.set(pw.value)"
            />
            @if (passwordError()) {
              <p class="field-error">{{ passwordError() }}</p>
            }
          </div>
          <button type="submit" class="btn" [disabled]="!newPassword()">Set password</button>
        </form>
      }
    }
  `,
})
export class UserForm {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmService);
  private readonly auth = inject(AuthService);
  private readonly idParam = inject(ActivatedRoute).snapshot.paramMap.get('id');

  protected readonly isEdit = this.idParam !== null;
  protected readonly id = this.idParam === null ? null : Number(this.idParam);
  protected readonly roles: Role[] = ['USER', 'SUPPORT_AGENT', 'ADMIN'];
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly loaded = signal<AuthUser | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly serverErrors = signal<Record<string, string>>({});
  protected readonly newPassword = signal('');
  protected readonly passwordError = signal<string | null>(null);
  protected readonly isSelf = computed(() => this.id !== null && this.auth.user()?.id === this.id);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', this.isEdit ? [] : [Validators.required]],
    fullName: [''],
    email: ['', [Validators.required, Validators.email]],
    role: ['USER' as Role, Validators.required],
    enabled: [true],
    password: ['', this.isEdit ? [] : [Validators.required, Validators.minLength(10)]],
  });

  constructor() {
    if (this.id !== null) {
      this.api.user(this.id).subscribe({
        next: (u) => {
          this.loaded.set(u);
          this.form.patchValue({
            fullName: u.fullName ?? '',
            email: u.email,
            role: u.role,
            enabled: u.enabled ?? true,
          });
          if (this.isSelf()) {
            this.form.controls.role.disable();
            this.form.controls.enabled.disable();
          }
        },
        error: (e) => this.loadError.set(errorMessage(e)),
      });
    }
  }

  protected error(field: string): string | null {
    const control = this.form.get(field);
    if (control && control.invalid && control.touched) {
      return field === 'email'
        ? 'Enter a valid email address.'
        : field === 'password'
          ? 'At least 10 characters.'
          : 'Required.';
    }
    return this.serverErrors()[field] ?? null;
  }

  protected async save(): Promise<void> {
    this.serverErrors.set({});
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const previous = this.loaded();
    if (previous && previous.enabled && !v.enabled) {
      const ok = await this.confirm.ask({
        title: `Deactivate ${previous.username}?`,
        message:
          'They will be signed out, cannot sign in again, and are removed from their active tickets.',
        confirmLabel: 'Deactivate',
        danger: true,
      });
      if (!ok) {
        return;
      }
    }
    this.saving.set(true);
    const call =
      this.id === null
        ? this.api.createUser({
            username: v.username,
            email: v.email,
            fullName: v.fullName || null,
            password: v.password,
            role: v.role,
          })
        : this.api.updateUser(this.id, {
            email: v.email,
            fullName: v.fullName || null,
            role: v.role,
            enabled: v.enabled,
          });
    call.subscribe({
      next: (u) => {
        this.notifications.success(
          this.isEdit ? `${u.username} updated.` : `${u.username} created.`,
        );
        void this.router.navigate(['/admin/users']);
      },
      error: (e) => {
        this.saving.set(false);
        this.serverErrors.set(fieldErrors(e));
        this.notifications.error(errorMessage(e));
      },
    });
  }

  protected resetPassword(event: Event): void {
    event.preventDefault();
    if (this.id === null) {
      return;
    }
    this.passwordError.set(null);
    this.api.resetPassword(this.id, this.newPassword()).subscribe({
      next: () => {
        this.newPassword.set('');
        this.notifications.success('Password changed.');
      },
      error: (e) => this.passwordError.set(fieldErrors(e)['newPassword'] ?? errorMessage(e)),
    });
  }
}
