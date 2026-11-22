import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, switchMap, tap } from 'rxjs';
import { NotificationService } from '../shared/notification';
import { AuthUser } from './auth.model';

/** Endpoints implemented by the backend's `AuthController` / `SecurityConfig`. */
export const AUTH_URLS = {
  login: '/api/auth/login',
  logout: '/api/auth/logout',
  me: '/api/auth/me',
  csrf: '/api/auth/csrf',
} as const;

/**
 * The single place that knows who is signed in.
 *
 * Authentication is a server-side session in an HttpOnly cookie, which the browser manages; nothing about the
 * session or the password is ever kept in JavaScript storage. After a page refresh the state is rebuilt by asking
 * the server (`GET /api/auth/me`). CSRF: the backend sets the readable `XSRF-TOKEN` cookie and expects it back in
 * the `X-XSRF-TOKEN` header, which Angular's HttpClient does for POST/PUT/DELETE automatically.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  private readonly _user = signal<AuthUser | null>(null);
  private readonly _initialized = signal(false);

  /** The signed-in account, or null. */
  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);
  readonly role = computed(() => this._user()?.role ?? null);
  readonly isAdmin = computed(() => this._user()?.role === 'ADMIN');
  /** ADMIN or SUPPORT_AGENT. Only decides what the UI shows; the server enforces every permission itself. */
  readonly isStaff = computed(() => {
    const role = this._user()?.role;
    return role === 'ADMIN' || role === 'SUPPORT_AGENT';
  });
  /** True once the server has been asked at least once who is signed in. */
  readonly initialized = this._initialized.asReadonly();

  /** Asks the backend to set the CSRF cookie (needed before login, and after logout removed it). */
  csrf(): Observable<void> {
    return this.http.get<void>(AUTH_URLS.csrf);
  }

  /** Obtains a CSRF token, then signs in. Errors (401 wrong credentials, 400, 403, network) are passed to the caller. */
  login(username: string, password: string): Observable<AuthUser> {
    return this.csrf().pipe(
      switchMap(() => this.http.post<AuthUser>(AUTH_URLS.login, { username, password })),
      tap((user) => {
        this._user.set(user);
        this._initialized.set(true);
      }),
    );
  }

  /**
   * Asks the server who is signed in. Any failure (401 or the server being unreachable) means "nobody"; it is never
   * an error for the caller, so startup cannot get stuck.
   */
  loadCurrentUser(): Observable<AuthUser | null> {
    return this.http.get<AuthUser>(AUTH_URLS.me).pipe(
      catchError(() => of(null)),
      tap((user) => {
        this._user.set(user);
        this._initialized.set(true);
      }),
    );
  }

  /** The current user, asking the server only if that has not happened yet. */
  ensureLoaded(): Observable<AuthUser | null> {
    return this._initialized() ? of(this._user()) : this.loadCurrentUser();
  }

  /**
   * Ends the session on the server, then clears local state and goes to the login page. Local state is cleared
   * even if the server could not be reached, so the UI never keeps showing protected pages; the user is told when
   * the server could not confirm the sign-out.
   */
  logout(): Observable<void> {
    return this.http.post<void>(AUTH_URLS.logout, null).pipe(
      map(() => undefined),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status !== 401) {
          this.notifications.error(
            'Could not confirm sign-out with the server. Close the browser if this computer is shared.',
          );
        }
        return of(undefined);
      }),
      finalize(() => {
        this.clear();
        void this.router.navigate(['/login']);
      }),
    );
  }

  /**
   * Called when the server answers 401 to a protected request: the session ended (timeout, logout elsewhere, account
   * disabled). Only acts while the app still thinks someone is signed in, so a burst of failing requests produces
   * one message and one redirect.
   */
  sessionExpired(): void {
    if (this._user() === null) {
      return;
    }
    this.clear();
    this.notifications.error('Your session has expired. Please sign in again.');
    const current = this.router.url;
    const extras =
      current && !current.startsWith('/login') ? { queryParams: { returnUrl: current } } : {};
    void this.router.navigate(['/login'], extras);
  }

  /** Replaces the cached account after the user edited their own profile. */
  updateCurrentUser(user: AuthUser): void {
    if (this._user()?.id === user.id) {
      this._user.set(user);
    }
  }

  private clear(): void {
    this._user.set(null);
    this._initialized.set(true);
  }
}
