import { Component, computed, effect, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ROLE_LABELS } from './auth/auth.model';
import { AuthService } from './auth/auth.service';
import { InboxService } from './inbox/inbox.service';
import { ConfirmDialog } from './shared/confirm';
import { Notifications } from './shared/notifications';

interface NavItem {
  path: string;
  label: string;
  exact?: boolean;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Notifications, ConfirmDialog],
  templateUrl: './app.html',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly inbox = inject(InboxService);
  protected readonly roleLabels = ROLE_LABELS;

  /** Navigation by role. Hiding a link is convenience only: the backend enforces every permission. */
  protected readonly workItems = computed<NavItem[]>(() => {
    const items: NavItem[] = [
      { path: '/dashboard', label: 'Dashboard' },
      { path: '/tickets/new', label: 'New ticket' },
      { path: '/tickets/mine', label: 'My tickets' },
    ];
    if (this.auth.isStaff()) {
      items.push(
        { path: '/tickets/assigned', label: 'Assigned to me' },
        { path: '/tickets/team', label: 'Team tickets' },
        { path: '/tickets/unassigned', label: 'Unassigned' },
        { path: '/tickets', label: 'All tickets', exact: true },
      );
    }
    return items;
  });

  protected readonly adminItems = computed<NavItem[]>(() =>
    this.auth.isAdmin()
      ? [
          { path: '/admin/users', label: 'Users' },
          { path: '/admin/teams', label: 'Teams' },
          { path: '/admin/sla', label: 'SLA targets' },
        ]
      : [],
  );

  protected readonly displayName = computed(() => {
    const user = this.auth.user();
    return user ? user.fullName || user.username : '';
  });

  constructor() {
    // Poll the unread-notification count only while someone is signed in.
    effect(() => {
      if (this.auth.isAuthenticated()) {
        this.inbox.startPolling();
      } else {
        this.inbox.stopPolling();
      }
    });
  }

  protected signOut(): void {
    this.auth.logout().subscribe();
  }
}
