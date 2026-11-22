import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests against a running stack (default: docker compose on http://localhost:8080).
 * The stack must have been started with a bootstrap administrator whose credentials are given in
 * E2E_ADMIN_USERNAME / E2E_ADMIN_PASSWORD; global-setup creates the other test accounts through the API.
 * These are throw-away test credentials for a local or CI database, never production ones.
 */
export default defineConfig({
  testDir: '.',
  testMatch: '**/*.e2e.ts',
  globalSetup: './global-setup.ts',
  fullyParallel: false,
  workers: 1,
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:8080',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
