import { Routes } from '@angular/router';
import { NotFound } from './not-found';
import { TicketDetail } from './tickets/ticket-detail';
import { TicketForm } from './tickets/ticket-form';
import { TicketList } from './tickets/ticket-list';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'tickets' },
  { path: 'tickets', component: TicketList, title: 'Tickets – Support Ticket Tracker' },
  { path: 'tickets/new', component: TicketForm, title: 'New ticket – Support Ticket Tracker' },
  { path: 'tickets/:id', component: TicketDetail, title: 'Ticket – Support Ticket Tracker' },
  { path: 'tickets/:id/edit', component: TicketForm, title: 'Edit ticket – Support Ticket Tracker' },
  { path: '**', component: NotFound, title: 'Not found – Support Ticket Tracker' },
];
