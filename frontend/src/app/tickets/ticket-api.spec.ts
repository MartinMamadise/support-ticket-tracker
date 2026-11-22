import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TicketApi, errorMessage, fieldErrors } from './ticket-api';
import { TicketRequest } from './ticket.model';

describe('TicketApi', () => {
  let api: TicketApi;
  let http: HttpTestingController;

  const request: TicketRequest = {
    title: 'VPN down',
    description: 'Cannot connect',
    status: 'OPEN',
    priority: 'HIGH',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(TicketApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const emptyPage = {
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
  };

  it('lists tickets with GET /api/tickets and returns the page', () => {
    let result: unknown;
    api.list().subscribe((page) => (result = page));
    const req = http.expectOne('/api/tickets');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush(emptyPage);
    expect(result).toEqual(emptyPage);
  });

  it('sends paging, sorting, filter and search parameters', () => {
    api
      .list({
        page: 2,
        size: 10,
        sortBy: 'priority',
        direction: 'desc',
        status: ['OPEN', 'IN_PROGRESS'],
        priority: ['HIGH'],
        createdFrom: '2026-11-01',
        createdTo: '2026-11-30',
        q: 'vpn',
      })
      .subscribe();
    const req = http.expectOne((r) => r.url === '/api/tickets');
    const p = req.request.params;
    expect(p.get('page')).toBe('2');
    expect(p.get('size')).toBe('10');
    expect(p.get('sortBy')).toBe('priority');
    expect(p.get('direction')).toBe('desc');
    expect(p.getAll('status')).toEqual(['OPEN', 'IN_PROGRESS']);
    expect(p.getAll('priority')).toEqual(['HIGH']);
    expect(p.get('createdFrom')).toBe('2026-11-01');
    expect(p.get('createdTo')).toBe('2026-11-30');
    expect(p.get('q')).toBe('vpn');
    req.flush(emptyPage);
  });

  it('omits empty parameters but keeps page 0', () => {
    api.list({ page: 0, q: '', status: [], priority: undefined }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/tickets');
    expect(req.request.params.keys()).toEqual(['page']);
    expect(req.request.params.get('page')).toBe('0');
    req.flush(emptyPage);
  });

  it('gets one ticket with GET /api/tickets/:id', () => {
    api.get(4).subscribe();
    const req = http.expectOne('/api/tickets/4');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('creates with POST and sends the body', () => {
    api.create(request).subscribe();
    const req = http.expectOne('/api/tickets');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('updates with PUT /api/tickets/:id', () => {
    api.update(4, request).subscribe();
    const req = http.expectOne('/api/tickets/4');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('deletes with DELETE /api/tickets/:id', () => {
    api.delete(4).subscribe();
    const req = http.expectOne('/api/tickets/4');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});

describe('error helpers', () => {
  it('explains when the server cannot be reached', () => {
    const err = new HttpErrorResponse({ status: 0 });
    expect(errorMessage(err)).toContain('Cannot reach the server');
  });

  it('uses the message from the API error body', () => {
    const err = new HttpErrorResponse({
      status: 404,
      error: { message: 'Ticket not found with ID: 9' },
    });
    expect(errorMessage(err)).toBe('Ticket not found with ID: 9');
  });

  it('falls back to the status code when the body has no message', () => {
    const err = new HttpErrorResponse({ status: 500, error: 'boom' });
    expect(errorMessage(err)).toBe('Request failed (500).');
  });

  it('extracts field errors', () => {
    const err = new HttpErrorResponse({
      status: 400,
      error: { fieldErrors: { title: 'Title is required' } },
    });
    expect(fieldErrors(err)).toEqual({ title: 'Title is required' });
    expect(fieldErrors(new Error('x'))).toEqual({});
  });
});
