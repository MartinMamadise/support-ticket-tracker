import { formatBytes, formatDuration, formatMinutes, formatRemaining } from './format';

describe('format helpers', () => {
  it('formats durations compactly', () => {
    expect(formatDuration(30)).toBe('< 1 min');
    expect(formatDuration(45 * 60)).toBe('45 min');
    expect(formatDuration(2 * 3600 + 15 * 60)).toBe('2 h 15 min');
    expect(formatDuration(3 * 86400 + 4 * 3600)).toBe('3 d 4 h');
    expect(formatDuration(-3600)).toBe('1 h');
  });

  it('describes remaining SLA time', () => {
    expect(formatRemaining(7200)).toBe('2 h left');
    expect(formatRemaining(-900)).toBe('overdue by 15 min');
    expect(formatRemaining(null)).toBeNull();
  });

  it('formats sizes and SLA minutes', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2.0 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB');
    expect(formatMinutes(480)).toBe('8 h');
  });
});
