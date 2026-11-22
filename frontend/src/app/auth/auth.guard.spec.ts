import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { authGuard, guestGuard } from './auth.guard';

@Component({ template: 'protected' })
class Protected {}
@Component({ template: 'login page' })
class LoginPage {}

describe('route guards', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let router: Router;
  const thandi = { id: 1, username: 'thandi', email: 't@example.com' };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'login', component: LoginPage, canActivate: [guestGuard] },
          { path: 'dashboard', component: Protected, canActivate: [authGuard] },
          { path: 'tickets', component: Protected, canActivate: [authGuard] },
          { path: 'tickets/:id', component: Protected, canActivate: [authGuard] },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  /** Starts a navigation and answers the /me call the guard makes. */
  async function navigate(url: string, me: 'user' | 'anonymous'): Promise<void> {
    const done = harness.navigateByUrl(url);
    const req = await vi.waitFor(() => http.expectOne('/api/auth/me'));
    if (me === 'user') {
      req.flush(thandi);
    } else {
      req.flush({}, { status: 401, statusText: 'Unauthorized' });
    }
    await done;
  }

  it('authGuard sends anonymous users to /login and remembers the requested route', async () => {
    await navigate('/tickets/12?tab=notes', 'anonymous');
    expect(router.url).toBe('/login?returnUrl=%2Ftickets%2F12%3Ftab%3Dnotes');
  });

  it('authGuard lets a signed-in user through', async () => {
    await navigate('/tickets', 'user');
    expect(router.url).toBe('/tickets');
  });

  it('uses the real authentication state: after the first check it does not ask again', async () => {
    await navigate('/tickets', 'user');
    await harness.navigateByUrl('/tickets/3');
    http.expectNone('/api/auth/me');
    expect(router.url).toBe('/tickets/3');
  });

  it('guestGuard shows the login page to anonymous users', async () => {
    await navigate('/login', 'anonymous');
    expect(router.url).toBe('/login');
  });

  it('guestGuard sends a signed-in user away from /login to the app', async () => {
    await navigate('/login', 'user');
    expect(router.url).toBe('/dashboard');
  });

  it('guestGuard sends a signed-in user to a valid returnUrl, but ignores an external one', async () => {
    await navigate('/login?returnUrl=%2Ftickets%2F4', 'user');
    expect(router.url).toBe('/tickets/4');
    await harness.navigateByUrl('/login?returnUrl=https%3A%2F%2Fevil.example');
    expect(router.url).toBe('/dashboard');
  });
});
