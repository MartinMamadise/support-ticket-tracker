import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { App } from './app';
import { Role } from './auth/auth.model';
import { AuthService } from './auth/auth.service';

describe('App shell', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      // No routes: the shell is tested on its own, pages have their own specs.
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function signIn(role: Role, fullName: string | null = null): void {
    TestBed.inject(AuthService).login('thandi', 'pw').subscribe();
    http.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    http
      .expectOne('/api/auth/login')
      .flush({ id: 1, username: 'thandi', email: 'thandi@example.com', fullName, role });
  }

  /** The shell polls the unread count while signed in. */
  function answerUnread(count: number): void {
    http.match('/api/notifications/unread-count').forEach((r) => r.flush({ count }));
  }

  const navLabels = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('nav a')).map((a) => a.textContent?.trim());

  it('shows no navigation and no sign-out button while signed out', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('nav')).toBeNull();
    expect(
      Array.from(el.querySelectorAll('button')).some((b) => b.textContent?.includes('Sign out')),
    ).toBe(false);
    http.expectNone('/api/notifications/unread-count');
  });

  it('gives ordinary users only their own work items', async () => {
    signIn('USER');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    answerUnread(0);
    await fixture.whenStable();
    expect(navLabels(fixture.nativeElement)).toEqual(['Dashboard', 'New ticket', 'My tickets']);
  });

  it('gives agents the queues and admins the administration section', async () => {
    signIn('ADMIN', 'Thandi Mokoena');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    answerUnread(3);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(navLabels(el)).toEqual([
      'Dashboard',
      'New ticket',
      'My tickets',
      'Assigned to me',
      'Team tickets',
      'Unassigned',
      'All tickets',
      'Users',
      'Teams',
      'SLA targets',
    ]);
    expect(el.querySelector('.user-name')?.textContent).toContain('Thandi Mokoena');
    expect(el.querySelector('.user-role')?.textContent).toContain('Administrator');
    expect(el.querySelector('.bell-count')?.textContent?.trim()).toBe('3');
  });

  it('signing out calls the backend, hides the navigation and goes to the login page', async () => {
    signIn('SUPPORT_AGENT');
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    answerUnread(0);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Sign out')!
      .click();
    const req = http.expectOne('/api/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('nav')).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
