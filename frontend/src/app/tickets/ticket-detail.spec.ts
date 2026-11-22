import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ConfirmService } from '../shared/confirm';
import { NotificationService } from '../shared/notification';
import { REQUESTER_PERMISSIONS, makeTicket } from '../testing/fixtures';
import { TicketDetail } from './ticket-detail';
import { Activity, Comment, Ticket } from './ticket.model';

const comment = (id: number, overrides: Partial<Comment> = {}): Comment => ({
  id,
  ticketId: 5,
  author: { id: 2, username: 'sam.agent', displayName: 'Sam Agent' },
  body: `Comment ${id}`,
  internal: false,
  edited: false,
  createdAt: '2026-11-21T09:30:00Z',
  updatedAt: '2026-11-21T09:30:00Z',
  canEdit: false,
  ...overrides,
});

const created: Activity = {
  id: 1,
  type: 'CREATED',
  actor: { id: 3, username: 'una.user', displayName: 'Una User' },
  entityType: 'TICKET',
  entityId: 5,
  field: null,
  oldValue: null,
  newValue: null,
  internal: false,
  createdAt: '2026-11-21T08:00:00Z',
};

describe('TicketDetail', () => {
  let fixture: ComponentFixture<TicketDetail>;
  let http: HttpTestingController;
  let confirm: ConfirmService;

  function create(ticket: Ticket, comments: Comment[] = []): void {
    TestBed.configureTestingModule({
      imports: [TicketDetail],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: String(ticket.id) }) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    confirm = TestBed.inject(ConfirmService);
    fixture = TestBed.createComponent(TicketDetail);
    http.expectOne(`/api/tickets/${ticket.id}`).flush(ticket);
    http.expectOne(`/api/tickets/${ticket.id}/comments`).flush(comments);
    http.expectOne(`/api/tickets/${ticket.id}/activity`).flush([created]);
    http.expectOne(`/api/tickets/${ticket.id}/attachments`).flush([]);
    if (ticket.permissions?.canAssign) {
      http.expectOne('/api/teams?includeInactive=false').flush([]);
      http.expectOne('/api/users/assignable').flush([]);
    }
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const buttons = () =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
  const button = (label: string) => buttons().find((b) => b.textContent?.trim() === label);

  afterEach(() => http.verify());

  it('shows the ticket, its people, SLA and timeline', () => {
    create(
      makeTicket({
        id: 5,
        title: 'Monitor flickers',
        assignee: { id: 2, username: 'sam.agent', displayName: 'Sam Agent' },
      }),
    );
    expect(text()).toContain('Monitor flickers');
    expect(text()).toContain('Una User');
    expect(text()).toContain('Sam Agent');
    expect(text()).toContain('Service level');
    expect(text()).toContain('created the ticket');
  });

  it('offers exactly the status moves the server allows and sends the version', () => {
    create(
      makeTicket({
        id: 5,
        status: 'IN_PROGRESS',
        version: 4,
        allowedTransitions: ['OPEN', 'WAITING', 'RESOLVED', 'CANCELLED'],
      }),
    );
    expect(button('Resolve')).toBeTruthy();
    expect(button('Wait for requester')).toBeTruthy();
    expect(button('Start work')).toBeUndefined();

    button('Resolve')!.click();
    const req = http.expectOne('/api/tickets/5/status');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ status: 'RESOLVED', version: 4 });
    req.flush(makeTicket({ id: 5, status: 'RESOLVED', allowedTransitions: ['OPEN', 'CLOSED'] }));
    http.expectOne('/api/tickets/5/activity').flush([created]);
    fixture.detectChanges();
    expect(button('Reopen')).toBeTruthy();
  });

  it('asks before cancelling and does nothing when the user declines', async () => {
    create(makeTicket({ id: 5 }));
    button('Cancel ticket')!.click();
    expect(confirm.pending()).not.toBeNull();
    confirm.answer(false);
    await Promise.resolve();
    http.expectNone('/api/tickets/5/status');
  });

  it('lets staff post internal notes', () => {
    create(makeTicket({ id: 5 }));
    const checkbox = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(
      '.composer input[type=checkbox]',
    )!;
    checkbox.click();
    const textarea = (fixture.nativeElement as HTMLElement).querySelector<HTMLTextAreaElement>(
      '#comment',
    )!;
    textarea.value = 'Replaced toner';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLFormElement>('.composer')!
      .dispatchEvent(new Event('submit'));

    const req = http.expectOne('/api/tickets/5/comments');
    expect(req.request.body).toEqual({ body: 'Replaced toner', internal: true });
    req.flush(comment(9, { body: 'Replaced toner', internal: true }));
    http.expectOne('/api/tickets/5').flush(makeTicket({ id: 5 }));
    http.expectOne('/api/tickets/5/activity').flush([created]);
    fixture.detectChanges();
    expect(text()).toContain('Internal note');
  });

  it('shows requesters no assignment panel and no internal-note option', () => {
    create(makeTicket({ id: 5, permissions: REQUESTER_PERMISSIONS }));
    expect(text()).not.toContain('Assignment');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.composer input[type=checkbox]'),
    ).toBeNull();
    expect(text()).not.toContain('Delete');
  });

  it('renders comment text as text, never as HTML', () => {
    create(makeTicket({ id: 5 }), [comment(1, { body: '<img src=x onerror="alert(1)">' })]);
    expect((fixture.nativeElement as HTMLElement).querySelector('.comment-body img')).toBeNull();
    expect(text()).toContain('<img src=x onerror="alert(1)">');
  });

  it('rejects disallowed file types before uploading', () => {
    create(makeTicket({ id: 5 }));
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#file')!;
    Object.defineProperty(input, 'files', { value: [new File(['MZ'], 'tool.exe')] });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(text()).toContain('Allowed file types');
    http.expectNone('/api/tickets/5/attachments');
  });

  it('shows the server message when the ticket cannot be loaded', () => {
    TestBed.configureTestingModule({
      imports: [TicketDetail],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '77' }) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(NotificationService);
    fixture = TestBed.createComponent(TicketDetail);
    http
      .expectOne('/api/tickets/77')
      .flush({ message: 'Ticket not found with ID: 77' }, { status: 404, statusText: 'Not Found' });
    for (const suffix of ['comments', 'activity', 'attachments']) {
      const pending = http.match(`/api/tickets/77/${suffix}`);
      pending.forEach((r) => (r.cancelled ? undefined : r.flush([])));
    }
    fixture.detectChanges();
    expect(text()).toContain('Ticket not found with ID: 77');
  });
});
