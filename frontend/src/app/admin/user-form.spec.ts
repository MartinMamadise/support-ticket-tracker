import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { UserForm } from './user-form';

describe('UserForm (administration)', () => {
  function setup(id: string | null) {
    TestBed.configureTestingModule({
      imports: [UserForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } },
        },
      ],
    });
    const fixture = TestBed.createComponent(UserForm);
    fixture.detectChanges();
    return {
      fixture,
      http: TestBed.inject(HttpTestingController),
      el: fixture.nativeElement as HTMLElement,
    };
  }

  const type = (el: HTMLElement, selector: string, value: string) => {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  it('creates accounts with the least privileged role unless another is chosen', () => {
    const { fixture, http, el } = setup(null);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    type(el, '#username', 'new.person');
    type(el, '#email', 'new@example.com');
    type(el, '#password', 'Welcome-2026x');
    fixture.detectChanges();
    el.querySelector('form')!.dispatchEvent(new Event('submit'));

    const req = http.expectOne('/api/admin/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({ username: 'new.person', role: 'USER' });
    req.flush({ id: 9, username: 'new.person', email: 'new@example.com', role: 'USER' });
  });

  it('shows server field errors', () => {
    const { fixture, http, el } = setup(null);
    type(el, '#username', 'taken');
    type(el, '#email', 'taken@example.com');
    type(el, '#password', 'Welcome-2026x');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/admin/users').flush(
      {
        message: 'Username or email is already in use',
        fieldErrors: { username: 'Username is already in use' },
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    expect(el.textContent).toContain('Username is already in use');
  });

  it('edits an existing account without sending a username or password', () => {
    const { fixture, http, el } = setup('4');
    http.expectOne('/api/admin/users/4').flush({
      id: 4,
      username: 'sam',
      email: 'sam@example.com',
      role: 'SUPPORT_AGENT',
      enabled: true,
    });
    fixture.detectChanges();
    expect(el.querySelector('#username')).toBeNull();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    const req = http.expectOne('/api/admin/users/4');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      email: 'sam@example.com',
      fullName: null,
      role: 'SUPPORT_AGENT',
      enabled: true,
    });
    req.flush({
      id: 4,
      username: 'sam',
      email: 'sam@example.com',
      role: 'SUPPORT_AGENT',
      enabled: true,
    });
  });
});
