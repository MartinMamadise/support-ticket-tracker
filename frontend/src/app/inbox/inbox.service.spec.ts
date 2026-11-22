import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { InboxService, UNREAD_POLL_MS } from './inbox.service';

describe('InboxService', () => {
  let service: InboxService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InboxService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    service.stopPolling();
    vi.useRealTimers();
  });

  it('polls the unread count while running and resets it when stopped', () => {
    vi.useFakeTimers();
    service.startPolling();
    http.expectOne('/api/notifications/unread-count').flush({ count: 4 });
    expect(service.unread()).toBe(4);
    vi.advanceTimersByTime(UNREAD_POLL_MS);
    http.expectOne('/api/notifications/unread-count').flush({ count: 5 });
    expect(service.unread()).toBe(5);
    service.stopPolling();
    expect(service.unread()).toBe(0);
    vi.advanceTimersByTime(UNREAD_POLL_MS);
    http.expectNone('/api/notifications/unread-count');
  });

  it('marking read updates the badge', () => {
    service.refreshUnread();
    http.expectOne('/api/notifications/unread-count').flush({ count: 2 });
    service.markRead(7).subscribe();
    const req = http.expectOne('/api/notifications/7/read');
    expect(req.request.method).toBe('PUT');
    req.flush({ id: 7, read: true });
    expect(service.unread()).toBe(1);
    service.markAllRead().subscribe();
    http.expectOne('/api/notifications/read-all').flush({ updated: 1 });
    expect(service.unread()).toBe(0);
  });

  it('lists with paging and the unread filter', () => {
    service.list(2, 10, true).subscribe();
    const req = http.expectOne((r) => r.url === '/api/notifications');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('unreadOnly')).toBe('true');
    req.flush({
      content: [],
      page: 2,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
      hasPrevious: true,
    });
  });
});
