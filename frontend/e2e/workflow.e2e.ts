import { expect, test } from '@playwright/test';
import { apiAs, signIn, signOut } from './support';

/**
 * The main service-desk workflow through the real UI and API: a user raises a ticket, an agent picks it up, talks to
 * the requester, keeps an internal note, resolves it, and the requester reopens it. Along the way it checks role
 * restrictions, ticket ownership and notifications.
 */
test.describe.serial('ticket workflow', () => {
  const title = `E2E printer ${Date.now()}`;
  let ticketUrl = '';

  test('a user signs in, sees only their navigation and raises a ticket', async ({ page }) => {
    await signIn(page, 'alice');
    const nav = page.getByRole('navigation', { name: 'Main' });
    await expect(nav.getByRole('link', { name: 'My tickets' })).toBeVisible();
    await expect(nav.getByRole('link', { name: 'Users' })).toHaveCount(0);

    await nav.getByRole('link', { name: 'New ticket' }).click();
    await page.getByLabel('Title').fill(title);
    await page.getByLabel('Description').fill('The floor 2 printer shows error E-14.');
    await page.getByLabel('Priority').selectOption('HIGH');
    await page.getByLabel('Category').selectOption('HARDWARE');
    await page.getByRole('button', { name: 'Create ticket' }).click();

    await expect(page.getByRole('heading', { name: title })).toBeVisible();
    await expect(page.getByText('Open', { exact: true }).first()).toBeVisible();
    ticketUrl = new URL(page.url()).pathname;
    expect(ticketUrl).toMatch(/^\/tickets\/\d+$/);
  });

  test('another user cannot open it, and users cannot reach administration', async ({ page }) => {
    await signIn(page, 'bob');
    await page.goto(ticketUrl);
    await expect(page.getByRole('alert')).toContainText('not found');
    await page.goto('/admin/users');
    await expect(page).toHaveURL(/\/dashboard$/);

    const bob = await apiAs('bob');
    expect((await bob.get('/api/admin/users')).status()).toBe(403);
    expect((await bob.get(`/api${ticketUrl}`)).status()).toBe(404);
    await bob.dispose();
  });

  test('an agent takes the ticket, adds an internal note and a reply, and resolves it', async ({
    page,
  }) => {
    await signIn(page, 'agent');
    await page.goto(ticketUrl);
    await page.getByRole('button', { name: 'Assign to me' }).click();
    await expect(page.getByText('Assignment saved.')).toBeVisible();
    await page.getByRole('button', { name: 'Start work' }).click();
    await expect(page.getByRole('button', { name: 'Resolve' })).toBeVisible();

    await page.getByLabel('Internal note').check();
    await page.getByLabel('Internal note (staff only)').fill('E2E-SECRET: toner supplier delayed');
    await page.getByRole('button', { name: 'Add note' }).click();
    await expect(page.locator('.comment-internal')).toContainText('E2E-SECRET');

    await page.getByLabel('Add a comment').fill('Replaced the toner, please try again.');
    await page.getByRole('button', { name: 'Post comment' }).click();
    await expect(page.locator('.comment-body').last()).toHaveText(
      'Replaced the toner, please try again.',
    );

    await page.getByRole('button', { name: 'Resolve' }).click();
    await expect(page.getByRole('button', { name: 'Reopen' })).toBeVisible();
    await expect(page.getByText('resolved the ticket')).toBeVisible();
  });

  test('the requester sees the reply but never the internal note, is notified, and can reopen', async ({
    page,
  }) => {
    await signIn(page, 'alice');
    await expect(page.locator('.bell-count')).toBeVisible();
    await page.goto(ticketUrl);
    await expect(page.getByText('Replaced the toner, please try again.')).toBeVisible();
    await expect(page.getByText('E2E-SECRET')).toHaveCount(0);
    await expect(page.getByText('Internal note')).toHaveCount(0);

    const alice = await apiAs('alice');
    const comments = await (await alice.get(`/api${ticketUrl}/comments`)).text();
    expect(comments).not.toContain('E2E-SECRET');
    await alice.dispose();

    await page.getByRole('button', { name: 'Reopen' }).click();
    await expect(page.getByText('reopened the ticket')).toBeVisible();
    await signOut(page);
  });

  test('the dashboard reflects the tickets', async ({ page }) => {
    await signIn(page, 'agent');
    await expect(page.getByText('Response SLA met')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Agent workload' })).toBeVisible();
    await expect(page.locator('.bar-row', { hasText: 'Hardware' })).toBeVisible();
  });
});
