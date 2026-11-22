import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthUser } from '../auth/auth.model';

/** The signed-in account's own profile (`/api/profile`). */
@Injectable({ providedIn: 'root' })
export class ProfileApi {
  private readonly http = inject(HttpClient);

  get(): Observable<AuthUser> {
    return this.http.get<AuthUser>('/api/profile');
  }

  update(email: string, fullName: string | null): Observable<AuthUser> {
    return this.http.put<AuthUser>('/api/profile', { email, fullName });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>('/api/profile/password', { currentPassword, newPassword });
  }
}
