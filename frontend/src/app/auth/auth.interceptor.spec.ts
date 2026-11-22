import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { CSRF_FAILURE_MESSAGE, authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: AuthService;
  let navigate: ReturnType<typeof vi.spyOn>;

  const unauthorized = { status: 401, statusText: 'Unauthorized' };
  const csrfFailure = { status: 403, message: CSRF_FAILURE_MESSAGE };

  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    http.verify();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  function signIn(): void {
    auth.login('thandi', 'pw').subscribe();
    http.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/auth/login').flush({ id: 1, username: 'thandi', email: 't@example.com' });
  }

  it('ends the session on a 401 from a protected API call and still reports the error to the caller', () => {
    signIn();
    let status: number | undefined;
    client.get('/api/tickets').subscribe({ error: (e) => (status = e.status) });
    http.expectOne('/api/tickets').flush({}, unauthorized);
    expect(status).toBe(401);
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('redirects only once when several requests fail with 401', () => {
    signIn();
    client.get('/api/tickets').subscribe({ error: () => undefined });
    client.get('/api/tickets/1').subscribe({ error: () => undefined });
    http.expectOne('/api/tickets').flush({}, unauthorized);
    http.expectOne('/api/tickets/1').flush({}, unauthorized);
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('leaves a 401 from the login request to the login form (no redirect)', () => {
    signIn();
    let status: number | undefined;
    client.post('/api/auth/login', {}).subscribe({ error: (e) => (status = e.status) });
    http.expectOne('/api/auth/login').flush({}, unauthorized);
    expect(status).toBe(401);
    expect(auth.isAuthenticated()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('leaves a 401 from /api/auth/me alone', () => {
    signIn();
    client.get('/api/auth/me').subscribe({ error: () => undefined });
    http.expectOne('/api/auth/me').flush({}, unauthorized);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not touch errors other than 401 (400, 404, 500) or requests outside /api/', () => {
    signIn();
    const statuses: number[] = [];
    for (const [url, status] of [
      ['/api/tickets', 400],
      ['/api/tickets/9', 404],
      ['/api/tickets', 500],
      ['/assets/x.json', 401],
    ] as const) {
      client.get(url).subscribe({ error: (e) => statuses.push(e.status) });
      http.expectOne(url).flush({}, { status, statusText: 'x' });
    }
    expect(statuses).toEqual([400, 404, 500, 401]);
    expect(auth.isAuthenticated()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('on a CSRF rejection, fetches a fresh token and repeats the request once with the new header', () => {
    signIn();
    let result: unknown;
    client.post('/api/tickets', { title: 'x' }).subscribe((r) => (result = r));
    http.expectOne('/api/tickets').flush(csrfFailure, { status: 403, statusText: 'Forbidden' });

    document.cookie = 'XSRF-TOKEN=fresh-token; path=/'; // what GET /api/auth/csrf does in a browser
    http.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });

    const retry = http.expectOne('/api/tickets');
    expect(retry.request.method).toBe('POST');
    expect(retry.request.headers.get('X-XSRF-TOKEN')).toBe('fresh-token');
    retry.flush({ id: 5 });
    expect(result).toEqual({ id: 5 });
  });

  it('gives up after one CSRF retry', () => {
    signIn();
    let status: number | undefined;
    client.post('/api/tickets', {}).subscribe({ error: (e) => (status = e.status) });
    http.expectOne('/api/tickets').flush(csrfFailure, { status: 403, statusText: 'Forbidden' });
    http.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/tickets').flush(csrfFailure, { status: 403, statusText: 'Forbidden' });
    expect(status).toBe(403);
  });

  it('a retried request that then finds the session gone is handled as an expired session', () => {
    signIn();
    client.delete('/api/tickets/3').subscribe({ error: () => undefined });
    http.expectOne('/api/tickets/3').flush(csrfFailure, { status: 403, statusText: 'Forbidden' });
    http.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/tickets/3').flush({}, unauthorized);
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('does not retry other 403 responses', () => {
    signIn();
    let status: number | undefined;
    client.get('/api/tickets').subscribe({ error: (e) => (status = e.status) });
    http
      .expectOne('/api/tickets')
      .flush({ message: 'Access denied' }, { status: 403, statusText: 'Forbidden' });
    expect(status).toBe(403);
  });
});
