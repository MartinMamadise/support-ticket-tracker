import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { NotificationService } from '../shared/notification';
import { TicketForm } from './ticket-form';
import { Ticket } from './ticket.model';
import { makeTicket } from '../testing/fixtures';

const saved: Ticket = makeTicket({
  id: 12,
  title: 'VPN down',
  description: 'Cannot connect',
  priority: 'HIGH',
});

describe('TicketForm', () => {
  let fixture: ComponentFixture<TicketForm>;
  let http: HttpTestingController;
  let el: HTMLElement;
  let navigate: ReturnType<typeof vi.spyOn>;

  function setup(id: string | null) {
    TestBed.configureTestingModule({
      imports: [TicketForm],
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
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(TicketForm);
    el = fixture.nativeElement;
  }

  const input = (id: string) => el.querySelector(`#${id}`) as HTMLInputElement;
  const fill = (id: string, value: string) => {
    input(id).value = value;
    input(id).dispatchEvent(new Event('input'));
  };
  const submit = async () => {
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };

  afterEach(() => http.verify());

  it('create mode: blocks submission and shows messages when required fields are empty', async () => {
    setup(null);
    await fixture.whenStable();

    await submit();

    http.expectNone('/api/tickets');
    expect(el.textContent).toContain('Title is required.');
    expect(el.textContent).toContain('Description is required.');
  });

  it('create mode: treats whitespace-only text as empty', async () => {
    setup(null);
    await fixture.whenStable();
    fill('title', '   ');
    fill('description', '   ');
    await submit();

    http.expectNone('/api/tickets');
    expect(el.textContent).toContain('Title is required.');
  });

  it('create mode: posts trimmed values, notifies and navigates to the new ticket', async () => {
    setup(null);
    await fixture.whenStable();
    const notices = TestBed.inject(NotificationService);

    fill('title', '  VPN down ');
    fill('description', 'Cannot connect');
    input('priority').value = 'HIGH';
    input('priority').dispatchEvent(new Event('change'));
    await submit();

    const req = http.expectOne('/api/tickets');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      title: 'VPN down',
      description: 'Cannot connect',
      priority: 'HIGH',
      category: 'OTHER',
    });
    req.flush(saved, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(notices.notices()[0].text).toBe('Ticket #12 created.');
    expect(navigate).toHaveBeenCalledWith(['/tickets', 12]);
  });

  it('shows server validation errors and keeps the user on the form', async () => {
    setup(null);
    await fixture.whenStable();
    const notices = TestBed.inject(NotificationService);

    fill('title', 'Valid title');
    fill('description', 'Valid description');
    await submit();
    http.expectOne('/api/tickets').flush(
      {
        message: 'Validation failed',
        fieldErrors: { priority: 'Priority must be one of: LOW, MEDIUM, HIGH' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(el.textContent).toContain('Priority must be one of: LOW, MEDIUM, HIGH');
    expect(notices.notices()[0]).toMatchObject({ kind: 'error', text: 'Validation failed' });
    expect(navigate).not.toHaveBeenCalled();
    expect((el.querySelector('button[type=submit]') as HTMLButtonElement).disabled).toBe(false);
  });

  it('edit mode: loads the ticket into the form and sends a PUT', async () => {
    setup('12');
    http.expectOne('/api/tickets/12').flush(saved);
    await fixture.whenStable();

    expect(el.querySelector('h1')?.textContent).toContain('Edit ticket #12');
    expect(input('title').value).toBe('VPN down');
    expect(input('priority').value).toBe('HIGH');

    fill('title', 'VPN restored');
    await submit();

    const req = http.expectOne('/api/tickets/12');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.title).toBe('VPN restored');
    expect(req.request.body.version).toBe(0);
    req.flush({ ...saved, title: 'VPN restored' });
    await fixture.whenStable();

    expect(TestBed.inject(NotificationService).notices()[0].text).toBe('Ticket #12 updated.');
  });

  it('create mode: does not offer a status choice', async () => {
    setup(null);
    await fixture.whenStable();

    expect(el.querySelector('#status')).toBeNull();
  });

  it('edit mode: offers only the current status and the allowed transitions', async () => {
    setup('12');
    http.expectOne('/api/tickets/12').flush(saved);
    await fixture.whenStable();

    const options = Array.from(el.querySelectorAll('#status option')).map((o) =>
      o.textContent?.trim(),
    );
    expect(options).toEqual(['Open', 'In progress', 'Cancelled']);
  });

  it('edit mode: shows the workflow error when a status change is rejected', async () => {
    setup('12');
    http.expectOne('/api/tickets/12').flush(saved);
    await fixture.whenStable();
    await submit();

    http.expectOne('/api/tickets/12').flush(
      {
        message:
          'Cannot change ticket status from OPEN to RESOLVED. Allowed: [IN_PROGRESS, CANCELLED].',
      },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(TestBed.inject(NotificationService).notices()[0].text).toContain(
      'Cannot change ticket status',
    );
    expect(navigate).not.toHaveBeenCalled();
  });

  it('edit mode: shows the conflict message when the ticket was changed by someone else', async () => {
    setup('12');
    http.expectOne('/api/tickets/12').flush(saved);
    await fixture.whenStable();
    await submit();

    http
      .expectOne('/api/tickets/12')
      .flush(
        { message: 'The ticket was changed by someone else. Reload it and try again.' },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();

    expect(TestBed.inject(NotificationService).notices()[0]).toMatchObject({
      kind: 'error',
      text: 'The ticket was changed by someone else. Reload it and try again.',
    });
    expect(navigate).not.toHaveBeenCalled();
  });

  it('edit mode: shows an error when the ticket does not exist', async () => {
    setup('99');
    http
      .expectOne('/api/tickets/99')
      .flush({ message: 'Ticket not found with ID: 99' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'Ticket not found with ID: 99',
    );
    expect(el.querySelector('form')).toBeNull();
  });

  it('edit mode: rejects a non-numeric id without calling the API', async () => {
    setup('abc');
    await fixture.whenStable();
    expect(el.textContent).toContain('Invalid ticket number.');
  });
});
