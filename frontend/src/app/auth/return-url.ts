/** Where signed-in users go when no (valid) original route is known. */
export const DEFAULT_ROUTE = '/dashboard';

/**
 * Accepts a `returnUrl` only if it is an in-app path. Anything else (absolute URLs, protocol-relative `//host`,
 * backslash tricks, the login page itself) falls back to the default so the parameter cannot be used as an open redirect.
 */
export function safeReturnUrl(raw: unknown): string {
  if (typeof raw !== 'string' || raw.length === 0 || raw.length > 2048) {
    return DEFAULT_ROUTE;
  }
  if (
    !raw.startsWith('/') ||
    raw.startsWith('//') ||
    raw.includes('\\') ||
    /[\u0000-\u001f]/.test(raw)
  ) {
    return DEFAULT_ROUTE;
  }
  if (raw === '/login' || raw.startsWith('/login?') || raw.startsWith('/login/')) {
    return DEFAULT_ROUTE;
  }
  return raw;
}
