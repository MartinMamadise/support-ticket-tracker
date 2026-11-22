import { DEFAULT_ROUTE, safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it('keeps in-app paths including query strings', () => {
    expect(safeReturnUrl('/tickets/5/edit')).toBe('/tickets/5/edit');
    expect(safeReturnUrl('/tickets?status=OPEN&page=2')).toBe('/tickets?status=OPEN&page=2');
  });

  it.each([
    null,
    undefined,
    '',
    42,
    'tickets',
    'https://evil.example/x',
    '//evil.example',
    '/\\evil.example',
    '/a\nb',
  ])('falls back to the default for %j', (value) =>
    expect(safeReturnUrl(value)).toBe(DEFAULT_ROUTE),
  );

  it('never returns to the login page', () => {
    expect(safeReturnUrl('/login')).toBe(DEFAULT_ROUTE);
    expect(safeReturnUrl('/login?returnUrl=%2Ftickets')).toBe(DEFAULT_ROUTE);
  });
});
