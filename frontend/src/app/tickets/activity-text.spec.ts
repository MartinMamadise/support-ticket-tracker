import { describeActivity } from './activity-text';
import { Activity } from './ticket.model';

const entry = (overrides: Partial<Activity>): Activity => ({
  id: 1,
  type: 'CREATED',
  actor: null,
  entityType: 'TICKET',
  entityId: 1,
  field: null,
  oldValue: null,
  newValue: null,
  internal: false,
  createdAt: '2026-11-21T08:00:00Z',
  ...overrides,
});

describe('describeActivity', () => {
  it('uses labels instead of enum names', () => {
    expect(
      describeActivity(
        entry({ type: 'STATUS_CHANGED', oldValue: 'OPEN', newValue: 'IN_PROGRESS' }),
      ),
    ).toBe('changed the status from Open to In progress');
    expect(
      describeActivity(entry({ type: 'PRIORITY_CHANGED', oldValue: 'LOW', newValue: 'CRITICAL' })),
    ).toBe('changed the priority from Low to Critical');
  });

  it('describes assignment changes in both directions', () => {
    expect(describeActivity(entry({ type: 'ASSIGNEE_CHANGED', newValue: 'sam' }))).toBe(
      'assigned the ticket to sam',
    );
    expect(describeActivity(entry({ type: 'ASSIGNEE_CHANGED', oldValue: 'sam' }))).toBe(
      'removed the assignee (sam)',
    );
  });
});
