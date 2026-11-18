import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationService } from '../shared/notification';
import { TicketList } from './ticket-list';
import { Ticket } from './ticket.model';

const ticket = (id: number, overrides: Partial<Ticket> = {}): Ticket => ({
  id,
  title: `Ticket ${id}`,
  description: 'Details',
  status: 'OPEN',
  priority: 'MEDIUM',
  createdAt: '2026-11-01T09:00:00',
  updatedAt: '2026-11-02T10:30:00',
  ...overrides,
});

describe('TicketList', () => {
  let fixture: ComponentFixture<TicketList>;
  let http: HttpTestingController;
  let el: HTMLElement;

  async function setup(response: Ticket[] | 'error') {
    TestBed.configureTestingModule({
      imports: [TicketList],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TicketList);
    el = fixture.nativeElement;
    const req = http.expectOne('/api/tickets');
    if (response === 'error') {
      req.flush({ message: 'Database unavailable' }, { status: 500, statusText: 'Server Error' });
    } else {
      req.flush(response);
    }
    await fixture.whenStable();
  }

  const rows = () => Array.from(el.querySelectorAll('tbody tr'));
  const click = async (selector: string, index = 0) => {
    (el.querySelectorAll(selector)[index] as HTMLElement).click();
    await fixture.whenStable();
  };

  afterEach(() => http.verify());

  it('shows tickets newest first with status and priority', async () => {
    await setup([ticket(1), ticket(3, { status: 'RESOLVED', priority: 'HIGH' }), ticket(2)]);
    expect(rows().length).toBe(3);
    expect(rows()[0].textContent).toContain('Ticket 3');
    expect(rows()[0].textContent).toContain('Resolved');
    expect(rows()[0].textContent).toContain('High');
    expect(el.textContent).toContain('Showing 3 of 3 tickets');
  });

  it('shows an empty state when there are no tickets', async () => {
    await setup([]);
    expect(el.textContent).toContain('There are no tickets yet');
    expect(el.querySelector('table')).toBeNull();
  });

  it('shows the error message and retries on request', async () => {
    await setup('error');
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Database unavailable');

    await click('.panel-error button');
    http.expectOne('/api/tickets').flush([ticket(1)]);
    await fixture.whenStable();
    expect(rows().length).toBe(1);
  });

  it('filters by status', async () => {
    await setup([ticket(1), ticket(2, { status: 'CLOSED' })]);
    const select = el.querySelector('#status-filter') as HTMLSelectElement;
    select.value = 'CLOSED';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(rows().length).toBe(1);
    expect(rows()[0].textContent).toContain('Ticket 2');
  });

  it('searches by title text and by ticket number', async () => {
    await setup([ticket(1, { title: 'Printer jam' }), ticket(2, { title: 'VPN down' })]);
    const input = el.querySelector('#search') as HTMLInputElement;

    input.value = 'vpn';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(rows().length).toBe(1);
    expect(rows()[0].textContent).toContain('VPN down');

    input.value = '1';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(rows()[0].textContent).toContain('Printer jam');
  });

  it('shows a message when filters match nothing', async () => {
    await setup([ticket(1)]);
    const input = el.querySelector('#search') as HTMLInputElement;
    input.value = 'zzz';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(el.textContent).toContain('No tickets match the current filters');
  });

  it('asks for confirmation, then deletes and reports success', async () => {
    await setup([ticket(1), ticket(2)]);
    const notices = TestBed.inject(NotificationService);

    // First click only asks for confirmation - no request yet
    await click('tbody tr:first-child .actions button');
    expect(el.textContent).toContain('Delete this ticket?');

    await click('.btn-danger');
    const req = http.expectOne('/api/tickets/2');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(rows().length).toBe(1);
    expect(notices.notices()[0]).toMatchObject({ kind: 'success', text: 'Ticket #2 deleted.' });
  });

  it('does not delete when confirmation is cancelled', async () => {
    await setup([ticket(1)]);
    await click('tbody tr .actions button');
    await click('.actions .btn:not(.btn-danger)');
    expect(rows().length).toBe(1);
    expect(el.textContent).not.toContain('Delete this ticket?');
  });

  it('reports a failed delete and reloads the list', async () => {
    await setup([ticket(1)]);
    const notices = TestBed.inject(NotificationService);

    await click('tbody tr .actions button');
    await click('.btn-danger');
    http.expectOne('/api/tickets/1').flush(
      { message: 'Ticket not found with ID: 1' },
      { status: 404, statusText: 'Not Found' },
    );
    http.expectOne('/api/tickets').flush([]);
    await fixture.whenStable();

    expect(notices.notices()[0]).toMatchObject({ kind: 'error', text: 'Ticket not found with ID: 1' });
    expect(el.textContent).toContain('There are no tickets yet');
  });
});
