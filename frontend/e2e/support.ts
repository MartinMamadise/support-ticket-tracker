import { APIRequestContext, Page, expect, request } from '@playwright/test';

export const BASE_URL = process.env['E2E_BASE_URL'] ?? 'http://localhost:8080';
export const PASSWORD = process.env['E2E_PASSWORD'] ?? 'E2e-test-pass-42';

export const ACCOUNTS = {
  admin: {
    username: process.env['E2E_ADMIN_USERNAME'] ?? 'admin',
    password: process.env['E2E_ADMIN_PASSWORD'] ?? 'change-me-Admin-123',
  },
  agent: { username: 'e2e.agent', password: PASSWORD },
  alice: { username: 'e2e.alice', password: PASSWORD },
  bob: { username: 'e2e.bob', password: PASSWORD },
} as const;

export type AccountName = keyof typeof ACCOUNTS;

/** A signed-in API client (session cookie + CSRF header), for setup steps and API-level assertions. */
export async function apiAs(name: AccountName): Promise<APIRequestContext> {
  const ctx = await request.newContext({ baseURL: BASE_URL });
  await ctx.get('/api/auth/csrf');
  const login = await ctx.post('/api/auth/login', {
    data: ACCOUNTS[name],
    headers: await csrf(ctx),
  });
  expect(login.ok(), `login as ${name}`).toBeTruthy();
  return ctx;
}

export async function csrf(ctx: APIRequestContext): Promise<Record<string, string>> {
  const state = await ctx.storageState();
  const token = state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? '';
  return { 'X-XSRF-TOKEN': token };
}

export async function signIn(page: Page, name: AccountName): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Username or email').fill(ACCOUNTS[name].username);
  await page.getByLabel('Password', { exact: true }).fill(ACCOUNTS[name].password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
}

export async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login/);
}
