import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { Dashboard } from './dashboard';
import { Dashboard as DashboardData } from './dashboard-api';

const data = (overrides: Partial<DashboardData> = {}): DashboardData => ({
  scope: 'own',
  totals: {
    total: 5,
    open: 2,
    inProgress: 1,
    waiting: 1,
    resolved: 1,
    closed: 0,
    cancelled: 0,
    highPriority: 2,
    critical: 1,
    unassigned: 3,
    overdue: 1,
  },
  byStatus: { OPEN: 2, IN_PROGRESS: 1, WAITING: 1, RESOLVED: 1, CLOSED: 0, CANCELLED: 0 },
  byPriority: { LOW: 1, MEDIUM: 2, HIGH: 1, CRITICAL: 1 },
  byCategory: { HARDWARE: 3, SOFTWARE: 0, NETWORK: 2, ACCESS: 0, EMAIL: 0, OTHER: 0 },
  sla: {
    responseCompliancePercent: 75,
    responseMeasured: 4,
    resolutionCompliancePercent: null,
    resolutionMeasured: 0,
    responseBreachedActive: 1,
    atRiskActive: 0,
  },
  averageResolutionHours: 5.5,
  trend: [
    { date: '2026-11-20', created: 2, resolved: 0 },
    { date: '2026-11-21', created: 3, resolved: 1 },
  ],
  workload: null,
  ...overrides,
});

describe('Dashboard', () => {
  let http: HttpTestingController;

  function render(response: DashboardData | 'error') {
    TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Dashboard);
    const req = http.expectOne((r) => r.url === '/api/dashboard');
    if (response === 'error') {
      req.flush({ message: 'Database unavailable' }, { status: 500, statusText: 'Server Error' });
    } else {
      req.flush(response);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the server figures, never invented ones', () => {
    const el = render(data());
    expect(el.textContent).toContain('Tickets you raised.');
    expect(el.querySelector('.kpi-value')?.textContent?.trim()).toBe('2');
    expect(el.textContent).toContain('75%');
    expect(el.textContent).toContain('5.5 h');
    expect(el.textContent).not.toContain('Agent workload');
    const bars = Array.from(el.querySelectorAll('.bar-row')).map((r) => r.getAttribute('title'));
    expect(bars).toContain('Hardware: 3');
    expect(bars).toContain('Critical: 1');
  });

  it('shows workload tables when the server includes them', () => {
    const el = render(
      data({
        scope: 'all',
        workload: {
          agents: [
            {
              userId: 2,
              username: 'sam',
              displayName: 'Sam Agent',
              role: 'SUPPORT_AGENT',
              activeTickets: 12,
              urgentTickets: 2,
              overdueTickets: 1,
              level: 'HIGH',
            },
          ],
          teams: [],
          unassignedTickets: 3,
        },
      }),
    );
    expect(el.textContent).toContain('Agent workload');
    expect(el.textContent).toContain('Sam Agent');
  });

  it('has an empty state and an error state', () => {
    expect(render(data({ totals: { ...data().totals, total: 0 } })).textContent).toContain(
      'No tickets yet',
    );
    TestBed.resetTestingModule();
    const el = render('error');
    expect(el.textContent).toContain('Database unavailable');
    expect(TestBed.inject(AuthService).user()).toBeNull();
  });

  it('can show the trend as a table', () => {
    TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Dashboard);
    http.expectOne((r) => r.url === '/api/dashboard').flush(data());
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('svg.trend-svg')?.getAttribute('aria-label')).toContain(
      '5 tickets created, 1 resolved',
    );
    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Show table'))!
      .click();
    fixture.detectChanges();
    const rows = Array.from(el.querySelectorAll('table tbody tr')).map((r) =>
      Array.from(r.querySelectorAll('td')).map((td) => td.textContent?.trim()),
    );
    expect(rows).toContainEqual(['2026-11-21', '3', '1']);
  });
});
