import { APIRequestContext } from '@playwright/test';
import { ACCOUNTS, PASSWORD, apiAs, csrf } from './support';

/** Creates the test accounts and a team (idempotent: existing ones are reused). */
export default async function globalSetup(): Promise<void> {
  const admin = await apiAs('admin');
  const agentId = await ensureUser(admin, ACCOUNTS.agent.username, 'SUPPORT_AGENT');
  await ensureUser(admin, ACCOUNTS.alice.username, 'USER');
  await ensureUser(admin, ACCOUNTS.bob.username, 'USER');

  const teams = await (await admin.get('/api/teams?includeInactive=true')).json();
  let team = teams.find((t: { name: string }) => t.name === 'E2E Service Desk');
  if (!team) {
    team = await (
      await admin.post('/api/admin/teams', {
        data: { name: 'E2E Service Desk' },
        headers: await csrf(admin),
      })
    ).json();
  }
  await admin.put(`/api/admin/teams/${team.id}/members`, {
    data: { userIds: [agentId] },
    headers: await csrf(admin),
  });
  await admin.dispose();
}

async function ensureUser(
  admin: APIRequestContext,
  username: string,
  role: string,
): Promise<number> {
  const created = await admin.post('/api/admin/users', {
    data: {
      username,
      email: `${username}@example.com`,
      password: PASSWORD,
      role,
      fullName: username.replace('e2e.', 'E2E '),
    },
    headers: await csrf(admin),
  });
  if (created.ok()) {
    return (await created.json()).id;
  }
  const found = await (
    await admin.get(`/api/admin/users?q=${encodeURIComponent(username)}`)
  ).json();
  return found.content.find((u: { username: string }) => u.username === username).id;
}
