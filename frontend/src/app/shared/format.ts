/** Formatting helpers shared by several pages. Pure functions, easy to test. */

/** "2 h 15 min", "3 d 4 h", "45 min", "< 1 min". Sign is ignored. */
export function formatDuration(totalSeconds: number): string {
  const s = Math.abs(Math.round(totalSeconds));
  if (s < 60) {
    return '< 1 min';
  }
  const days = Math.floor(s / 86_400);
  const hours = Math.floor((s % 86_400) / 3_600);
  const minutes = Math.floor((s % 3_600) / 60);
  if (days > 0) {
    return hours > 0 ? `${days} d ${hours} h` : `${days} d`;
  }
  if (hours > 0) {
    return minutes > 0 ? `${hours} h ${minutes} min` : `${hours} h`;
  }
  return `${minutes} min`;
}

/** "due in 2 h", "overdue by 15 min". */
export function formatRemaining(seconds: number | null | undefined): string | null {
  if (seconds === null || seconds === undefined) {
    return null;
  }
  return seconds < 0 ? `overdue by ${formatDuration(seconds)}` : `${formatDuration(seconds)} left`;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** "120 min" -> "2 h"; used for SLA targets. */
export function formatMinutes(minutes: number): string {
  return formatDuration(minutes * 60);
}
