import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationService } from '../shared/notification';
import { TicketList } from './ticket-list';
import { Page, Ticket } from './ticket.model';
import { makeTicket } from '../testing/fixtures';

const ticket = (id: number, overrides: Partial<Ticket> = {}): Ticket =>
  makeTicket({
    id,
    title: `Ticket ${id}`,
    description: 'Details',
    updatedAt: '2026-11-02T10:30:00Z',
    ...overrides,
  });

/** A server page; paging metadata is derived from the arguments like the real API would. */
const page = (
  content: Ticket[],
  {
    number = 0,
    size = 20,
    total = content.length,
  }: { number?: number; size?: number; total?: number } = {},
): Page<Ticket> => {
  const totalPages = Math.ceil(total / size);
  return {
    content,
    page: number,
    size,
    totalElements: total,
    totalPages,
    hasNext: number + 1 < totalPages,
    hasPrevious: number > 0,
  };
};

describe('TicketList', () => {
  let fixture: ComponentFixture<TicketList>;
  let http: HttpTestingController;
  let el: HTMLElement;

  const expectList = (): TestRequest =>
    http.expectOne((r) => r.url === '/api/tickets' && r.method === 'GET');

  async function setup(response: Page<Ticket> | 'error') {
    TestBed.configureTestingModule({
      imports: [TicketList],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TicketList);
    el = fixture.nativeElement;
    const req = expectList();
    if (response === 'error') {
      req.flush({ message: 'Database unavailable' }, { status: 500, statusText: 'Server Error' });
    } else {
      req.flush(response);
    }
    await fixture.whenStable();
    return req;
  }

  /** Answers the list request that the last interaction triggered. */
  async function answer(response: Page<Ticket>): Promise<TestRequest> {
    const req = expectList();
    req.flush(response);
    await fixture.whenStable();
    return req;
  }

  const rows = () => Array.from(el.querySelectorAll('tbody tr'));
  const click = async (selector: string, index = 0) => {
    (el.querySelectorAll(selector)[index] as HTMLElement).click();
    await fixture.whenStable();
  };
  const choose = async (selector: string, value: string) => {
    const select = el.querySelector(selector) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  };
  const search = async (text: string) => {
    const input = el.querySelector('#search') as HTMLInputElement;
    input.value = text;
    input.dispatchEvent(new Event('input'));
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit', { cancelable: true }),
    );
    await fixture.whenStable();
  };

  afterEach(() => http.verify());

  it('asks the server for the first page, newest first, with no filters', async () => {
    const req = await setup(page([ticket(1)]));
    const p = req.request.params;
    expect(p.get('page')).toBe('0');
    expect(p.get('sortBy')).toBe('createdAt');
    expect(p.get('direction')).toBe('desc');
    expect(p.has('q')).toBe(false);
    expect(p.has('status')).toBe(false);
    expect(p.has('priority')).toBe(false);
  });

  it('shows the tickets in the order the server returned them', async () => {
    await setup(page([ticket(3, { status: 'RESOLVED', priority: 'HIGH' }), ticket(2), ticket(1)]));
    expect(rows().length).toBe(3);
    expect(rows()[0].textContent).toContain('Ticket 3');
    expect(rows()[0].textContent).toContain('Resolved');
    expect(rows()[0].textContent).toContain('High');
    expect(rows()[2].textContent).toContain('Ticket 1');
    expect(el.textContent).toContain('Showing 1–3 of 3 tickets');
  });

  it('shows the range and page number when there are several pages', async () => {
    await setup(page([ticket(21), ticket(22)], { number: 1, size: 20, total: 42 }));
    expect(el.textContent).toContain('Showing 21–22 of 42 tickets');
    expect(el.textContent).toContain('Page 2 of 3');
  });

  it('shows an empty state when there are no tickets at all', async () => {
    await setup(page([]));
    expect(el.textContent).toContain('There are no tickets yet');
    expect(el.querySelector('table')).toBeNull();
  });

  it('shows the error message and retries on request', async () => {
    await setup('error');
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Database unavailable');

    await click('.panel-error button');
    await answer(page([ticket(1)]));
    expect(rows().length).toBe(1);
  });

  it('filters by status on the server and returns to the first page', async () => {
    await setup(page([ticket(1), ticket(2)], { size: 1, total: 2 }));
    await click('.pager button', 1); // Next
    const next = await answer(page([ticket(1)], { number: 1, size: 1, total: 2 }));
    expect(next.request.params.get('page')).toBe('1');

    await choose('#status-filter', 'CLOSED');
    const req = await answer(page([ticket(9, { status: 'CLOSED' })]));
    expect(req.request.params.getAll('status')).toEqual(['CLOSED']);
    expect(req.request.params.get('page')).toBe('0');
    expect(rows()[0].textContent).toContain('Ticket 9');
  });

  it('filters by priority on the server', async () => {
    await setup(page([ticket(1)]));
    await choose('#priority-filter', 'HIGH');
    const req = await answer(page([ticket(2, { priority: 'HIGH' })]));
    expect(req.request.params.getAll('priority')).toEqual(['HIGH']);
    expect(rows()[0].textContent).toContain('High');
  });

  it('combines status, priority and search in one request', async () => {
    await setup(page([ticket(1)]));
    await choose('#status-filter', 'OPEN');
    await answer(page([ticket(1)]));
    await choose('#priority-filter', 'LOW');
    await answer(page([ticket(1)]));
    await search('vpn');
    const req = await answer(page([ticket(1)]));
    const p = req.request.params;
    expect(p.getAll('status')).toEqual(['OPEN']);
    expect(p.getAll('priority')).toEqual(['LOW']);
    expect(p.get('q')).toBe('vpn');
  });

  it('sends the search text only when the search is submitted', async () => {
    await setup(page([ticket(1, { title: 'Printer jam' }), ticket(2, { title: 'VPN down' })]));
    const input = el.querySelector('#search') as HTMLInputElement;
    input.value = 'vpn';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    http.expectNone((r) => r.url === '/api/tickets'); // typing alone does not hit the server

    (el.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit', { cancelable: true }),
    );
    const req = await answer(page([ticket(2, { title: 'VPN down' })]));
    expect(req.request.params.get('q')).toBe('vpn');
    expect(rows().length).toBe(1);
    expect(rows()[0].textContent).toContain('VPN down');
  });

  it('changes the sort order on the server', async () => {
    await setup(page([ticket(1)]));
    await choose('#sort', 'priority');
    const req = await answer(page([ticket(1)]));
    expect(req.request.params.get('sortBy')).toBe('priority');
    expect(req.request.params.get('direction')).toBe('desc');

    await choose('#sort', 'oldest');
    const oldest = await answer(page([ticket(1)]));
    expect(oldest.request.params.get('sortBy')).toBe('createdAt');
    expect(oldest.request.params.get('direction')).toBe('asc');
  });

  it('shows a message when filters match nothing and can clear them', async () => {
    await setup(page([ticket(1)]));
    await search('zzz');
    await answer(page([]));
    expect(el.textContent).toContain('No tickets match the current filters');
    expect(el.textContent).toContain('No tickets found');
    expect(el.textContent).not.toContain('There are no tickets yet');

    await click('.btn-link'); // Clear filters
    const req = await answer(page([ticket(1)]));
    expect(req.request.params.has('q')).toBe(false);
    expect(rows().length).toBe(1);
  });

  it('moves between pages with Next and Previous', async () => {
    await setup(page([ticket(2), ticket(1)], { size: 2, total: 5 }));
    const buttons = () => Array.from(el.querySelectorAll('.pager button')) as HTMLButtonElement[];
    expect(buttons()[0].disabled).toBe(true); // Previous on the first page
    expect(buttons()[1].disabled).toBe(false);

    await click('.pager button', 1);
    const next = await answer(page([ticket(4), ticket(3)], { number: 1, size: 2, total: 5 }));
    expect(next.request.params.get('page')).toBe('1');
    expect(rows()[0].textContent).toContain('Ticket 4');
    expect(el.textContent).toContain('Page 2 of 3');

    await click('.pager button', 0);
    const previous = await answer(page([ticket(2), ticket(1)], { size: 2, total: 5 }));
    expect(previous.request.params.get('page')).toBe('0');
  });

  it('disables Next on the last page', async () => {
    await setup(page([ticket(5)], { number: 2, size: 2, total: 5 }));
    const buttons = Array.from(el.querySelectorAll('.pager button')) as HTMLButtonElement[];
    expect(buttons[1].disabled).toBe(true);
  });

  it('hides the pager when there is only one page', async () => {
    await setup(page([ticket(1), ticket(2)]));
    expect(el.querySelector('.pager')).toBeNull();
  });

  it('asks for confirmation, then deletes, reports success and reloads the page', async () => {
    await setup(page([ticket(2), ticket(1)]));
    const notices = TestBed.inject(NotificationService);

    // First click only asks for confirmation - no request yet
    await click('tbody tr:first-child .actions button');
    expect(el.textContent).toContain('Delete this ticket?');

    await click('.btn-danger');
    const req = http.expectOne('/api/tickets/2');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    await answer(page([ticket(1)]));
    expect(rows().length).toBe(1);
    expect(notices.notices()[0]).toMatchObject({ kind: 'success', text: 'Ticket #2 deleted.' });
  });

  it('steps back a page when deleting the last ticket on the final page', async () => {
    await setup(page([ticket(3)], { number: 1, size: 2, total: 3 }));

    await click('tbody tr .actions button');
    await click('.btn-danger');
    http.expectOne('/api/tickets/3').flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    // Page 1 no longer exists (2 tickets left, 1 page): the server returns it empty with the new totals.
    const stale = expectList();
    expect(stale.request.params.get('page')).toBe('1');
    stale.flush(page([], { number: 1, size: 2, total: 2 }));
    await fixture.whenStable();

    const retry = await answer(page([ticket(2), ticket(1)], { size: 2, total: 2 }));
    expect(retry.request.params.get('page')).toBe('0');
    expect(rows().length).toBe(2);
  });

  it('does not delete when confirmation is cancelled', async () => {
    await setup(page([ticket(1)]));
    await click('tbody tr .actions button');
    await click('.actions .btn:not(.btn-danger)');
    expect(rows().length).toBe(1);
    expect(el.textContent).not.toContain('Delete this ticket?');
  });

  it('reports a failed delete and reloads the list', async () => {
    await setup(page([ticket(1)]));
    const notices = TestBed.inject(NotificationService);

    await click('tbody tr .actions button');
    await click('.btn-danger');
    http
      .expectOne('/api/tickets/1')
      .flush({ message: 'Ticket not found with ID: 1' }, { status: 404, statusText: 'Not Found' });
    await answer(page([]));

    expect(notices.notices()[0]).toMatchObject({
      kind: 'error',
      text: 'Ticket not found with ID: 1',
    });
    expect(el.textContent).toContain('There are no tickets yet');
  });
});
