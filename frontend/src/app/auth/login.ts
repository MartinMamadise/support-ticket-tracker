import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { errorMessage } from '../tickets/ticket-api';
import { AuthService } from './auth.service';
import { safeReturnUrl } from './return-url';

/** Whitespace-only text counts as empty (the backend rejects blank values too). */
function notBlank(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  return typeof value === 'string' && value.length > 0 && value.trim().length === 0
    ? { required: true }
    : null;
}

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, notBlank, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  protected readonly loading = signal(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  protected readonly submitted = signal(false);

  private readonly passwordInput = viewChild<ElementRef<HTMLInputElement>>('passwordInput');

  protected usernameError(): string | null {
    const control = this.form.controls.username;
    if (!(control.touched || this.submitted()) || control.valid) {
      return null;
    }
    if (control.hasError('maxlength')) {
      return 'Username or email must be at most 254 characters.';
    }
    return 'Enter your username or email.';
  }

  protected passwordError(): string | null {
    const control = this.form.controls.password;
    if (!(control.touched || this.submitted()) || control.valid) {
      return null;
    }
    if (control.hasError('maxlength')) {
      return 'Password must be at most 128 characters.';
    }
    return 'Enter your password.';
  }

  protected toggleShowPassword(): void {
    this.showPassword.update((v) => !v);
  }

  protected submit(): void {
    if (this.loading()) {
      return;
    }
    this.submitted.set(true);
    this.submitError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.loading.set(true);
    this.auth.login(username.trim(), password).subscribe({
      next: () => {
        this.loading.set(false);
        void this.router.navigateByUrl(
          safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl')),
        );
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.submitError.set(this.messageFor(error));
        this.form.controls.password.reset('');
        this.submitted.set(false);
        this.passwordInput()?.nativeElement.focus();
      },
    });
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 429) {
      return 'Too many attempts. Please wait a moment and try again.';
    }
    return errorMessage(error);
  }
}
