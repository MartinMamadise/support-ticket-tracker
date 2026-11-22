import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Role } from './auth.model';
import { adminGuard, staffGuard } from './auth.guard';
import { AuthService } from './auth.service';

@Component({ template: 'page' })
class Page {}

describe('role guards', () => {
  let router: Router;
  let http: HttpTestingController;

  function setup(role: Role): void {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'dashboard', component: Page },
          { path: 'admin/users', component: Page, canActivate: [adminGuard] },
          { path: 'tickets/assigned', component: Page, canActivate: [staffGuard] },
        ]),
      ],
    });
    router = TestBed.inject(Router);
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).login('x', 'pw').subscribe();
    http.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/auth/login').flush({ id: 1, username: 'x', email: 'x@example.com', role });
  }

  it('sends non-administrators from admin pages to the dashboard', async () => {
    setup('SUPPORT_AGENT');
    await router.navigateByUrl('/admin/users');
    expect(router.url).toBe('/dashboard');
    await router.navigateByUrl('/tickets/assigned');
    expect(router.url).toBe('/tickets/assigned');
  });

  it('keeps ordinary users out of staff queues', async () => {
    setup('USER');
    await router.navigateByUrl('/tickets/assigned');
    expect(router.url).toBe('/dashboard');
  });

  it('lets administrators in', async () => {
    setup('ADMIN');
    await router.navigateByUrl('/admin/users');
    expect(router.url).toBe('/admin/users');
  });
});
