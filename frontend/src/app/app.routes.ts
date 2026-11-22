import { Routes } from '@angular/router';
import { SlaSettings } from './admin/sla-settings';
import { TeamForm } from './admin/team-form';
import { TeamList } from './admin/team-list';
import { UserForm } from './admin/user-form';
import { UserList } from './admin/user-list';
import { adminGuard, authGuard, guestGuard, staffGuard } from './auth/auth.guard';
import { Login } from './auth/login';
import { Dashboard } from './dashboard/dashboard';
import { NotificationList } from './inbox/notification-list';
import { NotFound } from './not-found';
import { Profile } from './profile/profile';
import { TicketDetail } from './tickets/ticket-detail';
import { TicketForm } from './tickets/ticket-form';
import { TicketList } from './tickets/ticket-list';

const t = (title: string) => `${title} – SupportHub`;

export const routes: Routes = [
  { path: 'login', component: Login, canActivate: [guestGuard], title: t('Sign in') },
  {
    // Everything else requires a signed-in user. Role guards only control navigation; the API enforces permissions.
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: Dashboard, title: t('Dashboard') },
      { path: 'tickets', component: TicketList, data: { view: 'all' }, title: t('Tickets') },
      { path: 'tickets/new', component: TicketForm, title: t('New ticket') },
      {
        path: 'tickets/mine',
        component: TicketList,
        data: { view: 'mine' },
        title: t('My tickets'),
      },
      {
        path: 'tickets/assigned',
        component: TicketList,
        data: { view: 'assigned' },
        canActivate: [staffGuard],
        title: t('Assigned to me'),
      },
      {
        path: 'tickets/team',
        component: TicketList,
        data: { view: 'team' },
        canActivate: [staffGuard],
        title: t('Team tickets'),
      },
      {
        path: 'tickets/unassigned',
        component: TicketList,
        data: { view: 'unassigned' },
        canActivate: [staffGuard],
        title: t('Unassigned'),
      },
      { path: 'tickets/:id', component: TicketDetail, title: t('Ticket') },
      { path: 'tickets/:id/edit', component: TicketForm, title: t('Edit ticket') },
      { path: 'notifications', component: NotificationList, title: t('Notifications') },
      { path: 'profile', component: Profile, title: t('My profile') },
      {
        path: 'admin',
        canActivate: [adminGuard],
        children: [
          { path: 'users', component: UserList, title: t('Users') },
          { path: 'users/new', component: UserForm, title: t('New user') },
          { path: 'users/:id', component: UserForm, title: t('Edit user') },
          { path: 'teams', component: TeamList, title: t('Teams') },
          { path: 'teams/new', component: TeamForm, title: t('New team') },
          { path: 'teams/:id', component: TeamForm, title: t('Team') },
          { path: 'sla', component: SlaSettings, title: t('SLA targets') },
        ],
      },
      { path: '**', component: NotFound, title: t('Not found') },
    ],
  },
];
