import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { NotificationService } from '../shared/notification';
import { TicketDetail } from './ticket-detail';
import { Ticket } from './ticket.model';

const ticket: Ticket = {
  id: 5,
  title: 'Monitor flickers',
  description: 'Line one\nLine two',
  status: 'IN_PROGRESS',
  priority: 'LOW',
  createdAt: '2026-11-01T09:00:00',
  updatedAt: '2026-11-02T10:30:00',
};

describe('TicketDetail', () => {
  let fixture: ComponentFixture<TicketDetail>;
  let http: HttpTestingController;
  let el: HTMLElement;

  function setup(id: string) {
    TestBed.configureTestingModule({
      imports: [TicketDetail],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TicketDetail);
    el = fixture.nativeElement;
  }

  afterEach(() => http.verify());

  it('shows the ticket details', async () => {
    setup('5');
    http.expectOne('/api/tickets/5').flush(ticket);
    await fixture.whenStable();

    expect(el.querySelector('h1')?.textContent).toContain('#5');
    expect(el.querySelector('h1')?.textContent).toContain('Monitor flickers');
    expect(el.textContent).toContain('In progress');
    expect(el.textContent).toContain('Low');
    expect(el.textContent).toContain('02 Nov 2026, 10:30');
    expect(el.querySelector('.description')?.textContent).toContain('Line one\nLine two');
  });

  it('shows a not-found message for a missing ticket', async () => {
    setup('99');
    http.expectOne('/api/tickets/99').flush(
      { message: 'Ticket not found with ID: 99' },
      { status: 404, statusText: 'Not Found' },
    );
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Ticket not found with ID: 99');
  });

  it('rejects an invalid id without calling the API', async () => {
    setup('abc');
    await fixture.whenStable();
    expect(el.textContent).toContain('Invalid ticket number.');
  });

  it('deletes after confirmation, notifies and returns to the list', async () => {
    setup('5');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    http.expectOne('/api/tickets/5').flush(ticket);
    await fixture.whenStable();

    const buttons = () => Array.from(el.querySelectorAll('.header-actions button')) as HTMLButtonElement[];
    buttons()[0].click(); // "Delete" -> asks to confirm
    await fixture.whenStable();
    expect(el.textContent).toContain('Delete this ticket?');
    http.expectNone('/api/tickets/5');

    (el.querySelector('.btn-danger') as HTMLButtonElement).click();
    const req = http.expectOne('/api/tickets/5');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(TestBed.inject(NotificationService).notices()[0].text).toBe('Ticket #5 deleted.');
    expect(navigate).toHaveBeenCalledWith(['/tickets']);
  });
});
