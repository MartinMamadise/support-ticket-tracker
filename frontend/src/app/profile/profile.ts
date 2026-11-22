import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthUser, ROLE_LABELS } from '../auth/auth.model';
import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../shared/notification';
import { errorMessage, fieldErrors } from '../tickets/ticket-api';
import { ProfileApi } from './profile-api';

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, DatePipe],
  template: `
    <div class="page-header"><h1>My profile</h1></div>
    @if (user(); as u) {
      <div class="grid-2">
        <form class="panel form" [formGroup]="details" (ngSubmit)="saveDetails()" novalidate>
          <h2>Details</h2>
          <dl class="details">
            <dt>Username</dt>
            <dd>{{ u.username }}</dd>
            <dt>Role</dt>
            <dd>{{ roleLabels[u.role] }}</dd>
            <dt>Member since</dt>
            <dd>{{ u.createdAt | date: 'dd MMM yyyy' }}</dd>
          </dl>
          <div class="field">
            <label for="fullName">Full name</label>
            <input id="fullName" formControlName="fullName" maxlength="100" autocomplete="name" />
          </div>
          <div class="field">
            <label for="email">Email</label>
            <input id="email" type="email" formControlName="email" autocomplete="email" />
            @if (detailErrors()['email']; as m) {
              <p class="field-error">{{ m }}</p>
            }
          </div>
          <button type="submit" class="btn btn-primary" [disabled]="details.invalid">
            Save details
          </button>
        </form>

        <form class="panel form" [formGroup]="password" (ngSubmit)="changePassword()" novalidate>
          <h2>Change password</h2>
          <div class="field">
            <label for="currentPassword">Current password</label>
            <input
              id="currentPassword"
              type="password"
              formControlName="currentPassword"
              autocomplete="current-password"
            />
            @if (passwordErrors()['currentPassword']; as m) {
              <p class="field-error">{{ m }}</p>
            }
          </div>
          <div class="field">
            <label for="newPassword">New password</label>
            <input
              id="newPassword"
              type="password"
              formControlName="newPassword"
              autocomplete="new-password"
            />
            <p class="field-hint">At least 10 characters with a letter and a digit.</p>
            @if (passwordErrors()['newPassword']; as m) {
              <p class="field-error">{{ m }}</p>
            }
          </div>
          <button type="submit" class="btn" [disabled]="password.invalid">Change password</button>
        </form>
      </div>
    } @else if (error()) {
      <div class="panel panel-error" role="alert">
        <p>{{ error() }}</p>
      </div>
    } @else {
      <p class="muted" aria-busy="true">Loading profile…</p>
    }
  `,
})
export class Profile {
  private readonly api = inject(ProfileApi);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly roleLabels = ROLE_LABELS;
  protected readonly user = signal<AuthUser | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly detailErrors = signal<Record<string, string>>({});
  protected readonly passwordErrors = signal<Record<string, string>>({});

  protected readonly details = this.fb.group({
    fullName: [''],
    email: ['', [Validators.required, Validators.email]],
  });
  protected readonly password = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(10)]],
  });

  constructor() {
    this.api.get().subscribe({
      next: (u) => {
        this.user.set(u);
        this.details.patchValue({ fullName: u.fullName ?? '', email: u.email });
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected saveDetails(): void {
    this.detailErrors.set({});
    const v = this.details.getRawValue();
    this.api.update(v.email, v.fullName.trim() || null).subscribe({
      next: (u) => {
        this.user.set(u);
        this.auth.updateCurrentUser(u);
        this.notifications.success('Profile saved.');
      },
      error: (e) => {
        this.detailErrors.set(fieldErrors(e));
        this.notifications.error(errorMessage(e));
      },
    });
  }

  protected changePassword(): void {
    this.passwordErrors.set({});
    const v = this.password.getRawValue();
    this.api.changePassword(v.currentPassword, v.newPassword).subscribe({
      next: () => {
        this.password.reset();
        this.notifications.success('Password changed.');
      },
      error: (e) => this.passwordErrors.set(fieldErrors(e)),
    });
  }
}
