import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { Login } from './login';

describe('Login', () => {
  let http: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;
  let returnUrl: string | null;

  async function setup(url: string | null = null) {
    returnUrl = url;
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: { get: (k: string) => (k === 'returnUrl' ? returnUrl : null) },
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, el };
  }

  function type(el: HTMLElement, selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submit(fixture: Awaited<ReturnType<typeof setup>>['fixture']): Promise<void> {
    (fixture.nativeElement as HTMLElement)
      .querySelector('form')!
      .dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  const flushCsrf = () =>
    http.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });

  afterEach(() => http.verify());

  it('renders labelled username/email and password fields with proper autocomplete and a submit button', async () => {
    const { el } = await setup();
    expect(el.querySelector('label[for="login-username"]')?.textContent).toContain(
      'Username or email',
    );
    expect(el.querySelector('#login-username')?.getAttribute('autocomplete')).toBe('username');
    expect(el.querySelector('label[for="login-password"]')?.textContent).toContain('Password');
    const password = el.querySelector<HTMLInputElement>('#login-password')!;
    expect(password.type).toBe('password');
    expect(password.getAttribute('autocomplete')).toBe('current-password');
    expect(el.querySelector<HTMLButtonElement>('button[type=submit]')?.textContent).toContain(
      'Sign in',
    );
  });

  it('requires both fields and sends nothing when they are empty', async () => {
    const { fixture, el } = await setup();
    await submit(fixture);
    fixture.detectChanges();
    expect(el.querySelector('#login-username-error')?.textContent).toContain(
      'Enter your username or email',
    );
    expect(el.querySelector('#login-password-error')?.textContent).toContain('Enter your password');
    expect(el.querySelector('#login-username')?.getAttribute('aria-invalid')).toBe('true');
    http.expectNone('/api/auth/csrf');
    http.expectNone('/api/auth/login');
  });

  it('treats a whitespace-only username as empty', async () => {
    const { fixture, el } = await setup();
    type(el, '#login-username', '   ');
    type(el, '#login-password', 'pw');
    await submit(fixture);
    fixture.detectChanges();
    expect(el.querySelector('#login-username-error')).not.toBeNull();
    http.expectNone('/api/auth/login');
  });

  it('shows and hides the password', async () => {
    const { fixture, el } = await setup();
    const toggle = el.querySelector<HTMLButtonElement>('.password-row button')!;
    const password = el.querySelector<HTMLInputElement>('#login-password')!;
    expect(toggle.getAttribute('aria-pressed')).toBe('false');
    toggle.click();
    fixture.detectChanges();
    expect(password.type).toBe('text');
    expect(toggle.getAttribute('aria-pressed')).toBe('true');
    toggle.click();
    fixture.detectChanges();
    expect(password.type).toBe('password');
  });

  it('signs in against the backend, shows a loading state, and goes to the default route', async () => {
    const { fixture, el } = await setup();
    type(el, '#login-username', ' thandi ');
    type(el, '#login-password', 'correct horse');
    await submit(fixture);
    fixture.detectChanges();

    const button = el.querySelector<HTMLButtonElement>('button[type=submit]')!;
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain('Signing in');
    expect(el.querySelector('form')?.getAttribute('aria-busy')).toBe('true');

    flushCsrf();
    const login = http.expectOne('/api/auth/login');
    expect(login.request.body).toEqual({ username: 'thandi', password: 'correct horse' });
    login.flush({ id: 1, username: 'thandi', email: 't@example.com' });
    await fixture.whenStable();

    expect(TestBed.inject(AuthService).user()?.username).toBe('thandi');
    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('returns to the originally requested route after login', async () => {
    const { fixture, el } = await setup('/tickets/8/edit');
    type(el, '#login-username', 'thandi');
    type(el, '#login-password', 'pw');
    await submit(fixture);
    flushCsrf();
    http.expectOne('/api/auth/login').flush({ id: 1, username: 'thandi', email: 't@example.com' });
    expect(navigateByUrl).toHaveBeenCalledWith('/tickets/8/edit');
  });

  it('ignores an external returnUrl', async () => {
    const { fixture, el } = await setup('https://evil.example/');
    type(el, '#login-username', 'thandi');
    type(el, '#login-password', 'pw');
    await submit(fixture);
    flushCsrf();
    http.expectOne('/api/auth/login').flush({ id: 1, username: 'thandi', email: 't@example.com' });
    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('shows the backend message for wrong credentials, clears the password and re-enables the form', async () => {
    const { fixture, el } = await setup();
    type(el, '#login-username', 'thandi');
    type(el, '#login-password', 'wrong');
    await submit(fixture);
    flushCsrf();
    http
      .expectOne('/api/auth/login')
      .flush(
        { status: 401, message: 'Invalid username or password' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.querySelector('[role=alert]')?.textContent).toContain('Invalid username or password');
    expect(el.querySelector<HTMLInputElement>('#login-password')!.value).toBe('');
    expect(el.querySelector<HTMLInputElement>('#login-username')!.value).toBe('thandi');
    expect(el.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBe(false);
    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(false);
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('explains when the server cannot be reached', async () => {
    const { fixture, el } = await setup();
    type(el, '#login-username', 'thandi');
    type(el, '#login-password', 'pw');
    await submit(fixture);
    http.expectOne('/api/auth/csrf').error(new ProgressEvent('error'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('Cannot reach the server');
  });

  it('ignores a second submit while a login is in flight', async () => {
    const { fixture, el } = await setup();
    type(el, '#login-username', 'thandi');
    type(el, '#login-password', 'pw');
    await submit(fixture);
    await submit(fixture);
    flushCsrf();
    http.expectOne('/api/auth/login').flush({ id: 1, username: 'thandi', email: 't@example.com' });
  });
});
