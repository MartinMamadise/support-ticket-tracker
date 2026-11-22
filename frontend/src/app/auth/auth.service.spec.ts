import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { NotificationService } from '../shared/notification';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let router: Router;
  let navigate: ReturnType<typeof vi.spyOn>;

  const thandi = { id: 1, username: 'thandi', email: 'thandi@example.com' };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  function signIn(): void {
    auth.login('thandi', 'pw').subscribe();
    http.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/auth/login').flush(thandi);
  }

  it('starts unauthenticated and not initialized', () => {
    expect(auth.user()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.initialized()).toBe(false);
  });

  it('login fetches the CSRF cookie first, then posts the credentials and stores the user', () => {
    let result: unknown;
    auth.login('thandi', 'secret pw').subscribe((u) => (result = u));

    const csrf = http.expectOne('/api/auth/csrf');
    expect(csrf.request.method).toBe('GET');
    http.expectNone('/api/auth/login'); // not before the CSRF call has finished
    csrf.flush(null, { status: 204, statusText: 'No Content' });

    const login = http.expectOne('/api/auth/login');
    expect(login.request.method).toBe('POST');
    expect(login.request.body).toEqual({ username: 'thandi', password: 'secret pw' });
    login.flush(thandi);

    expect(result).toEqual(thandi);
    expect(auth.user()).toEqual(thandi);
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('a failed login leaves the user signed out and passes the error on', () => {
    let status: number | undefined;
    auth.login('thandi', 'wrong').subscribe({ error: (e) => (status = e.status) });
    http.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    http
      .expectOne('/api/auth/login')
      .flush(
        { status: 401, message: 'Invalid username or password' },
        { status: 401, statusText: 'Unauthorized' },
      );
    expect(status).toBe(401);
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('does not put the password, user or any session data into browser storage', () => {
    localStorage.clear();
    sessionStorage.clear();
    signIn();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('loadCurrentUser restores the user from GET /api/auth/me (page refresh)', () => {
    let result: unknown;
    auth.loadCurrentUser().subscribe((u) => (result = u));
    const req = http.expectOne('/api/auth/me');
    expect(req.request.method).toBe('GET');
    req.flush(thandi);
    expect(result).toEqual(thandi);
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.initialized()).toBe(true);
  });

  it('loadCurrentUser treats 401 as signed out, without an error', () => {
    let result: unknown = 'unset';
    auth.loadCurrentUser().subscribe((u) => (result = u));
    http.expectOne('/api/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(result).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.initialized()).toBe(true);
  });

  it('loadCurrentUser treats an unreachable server as signed out so startup cannot hang', () => {
    let result: unknown = 'unset';
    auth.loadCurrentUser().subscribe((u) => (result = u));
    http.expectOne('/api/auth/me').error(new ProgressEvent('error'));
    expect(result).toBeNull();
    expect(auth.initialized()).toBe(true);
  });

  it('ensureLoaded asks the server once and then answers from state', () => {
    auth.ensureLoaded().subscribe();
    http.expectOne('/api/auth/me').flush(thandi);
    let result: unknown;
    auth.ensureLoaded().subscribe((u) => (result = u));
    http.expectNone('/api/auth/me');
    expect(result).toEqual(thandi);
  });

  it('logout calls POST /api/auth/logout, clears the user and goes to the login page', () => {
    signIn();
    auth.logout().subscribe();
    const req = http.expectOne('/api/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(auth.user()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('logout still clears local state when the server cannot be reached, and says so', () => {
    const notices = TestBed.inject(NotificationService);
    signIn();
    auth.logout().subscribe();
    http.expectOne('/api/auth/logout').error(new ProgressEvent('error'));
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
    expect(notices.notices().some((n) => n.kind === 'error')).toBe(true);
  });

  it('logout with an already-ended session (401) just signs out quietly', () => {
    const notices = TestBed.inject(NotificationService);
    signIn();
    auth.logout().subscribe();
    http.expectOne('/api/auth/logout').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isAuthenticated()).toBe(false);
    expect(notices.notices()).toEqual([]);
  });

  describe('sessionExpired', () => {
    it('clears the user, tells the user and redirects to login once with the current route', () => {
      const notices = TestBed.inject(NotificationService);
      signIn();
      vi.spyOn(router, 'url', 'get').mockReturnValue('/tickets/7');

      auth.sessionExpired();
      auth.sessionExpired(); // a second failing request at the same time

      expect(auth.isAuthenticated()).toBe(false);
      expect(navigate).toHaveBeenCalledTimes(1);
      expect(navigate).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: '/tickets/7' },
      });
      expect(notices.notices().filter((n) => n.text.includes('expired')).length).toBe(1);
    });

    it('does nothing when nobody is signed in', () => {
      auth.sessionExpired();
      expect(navigate).not.toHaveBeenCalled();
    });

    it('does not remember the login page as the place to return to', () => {
      signIn();
      vi.spyOn(router, 'url', 'get').mockReturnValue('/login');
      auth.sessionExpired();
      expect(navigate).toHaveBeenCalledWith(['/login'], {});
    });
  });
});
