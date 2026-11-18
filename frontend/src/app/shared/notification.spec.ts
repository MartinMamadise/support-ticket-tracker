import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { NotificationService } from './notification';

describe('NotificationService', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('adds success and error notices', () => {
    const service = TestBed.inject(NotificationService);
    service.success('Saved');
    service.error('Failed');
    expect(service.notices().map((n) => [n.kind, n.text])).toEqual([
      ['success', 'Saved'],
      ['error', 'Failed'],
    ]);
  });

  it('dismisses a notice manually', () => {
    const service = TestBed.inject(NotificationService);
    service.success('Saved');
    service.dismiss(service.notices()[0].id);
    expect(service.notices()).toEqual([]);
  });

  it('removes a notice automatically after a delay', () => {
    const service = TestBed.inject(NotificationService);
    service.success('Saved');
    vi.advanceTimersByTime(5001);
    expect(service.notices()).toEqual([]);
  });
});
