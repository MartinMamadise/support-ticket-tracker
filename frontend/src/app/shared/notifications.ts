import { Component, inject } from '@angular/core';
import { NotificationService } from './notification';

@Component({
  selector: 'app-notifications',
  template: `
    <div class="notices" aria-live="polite">
      @for (notice of service.notices(); track notice.id) {
        <div
          class="notice"
          [class.notice-error]="notice.kind === 'error'"
          [attr.role]="notice.kind === 'error' ? 'alert' : 'status'"
        >
          <span>{{ notice.text }}</span>
          <button
            type="button"
            class="notice-close"
            aria-label="Dismiss message"
            (click)="service.dismiss(notice.id)"
          >
            &times;
          </button>
        </div>
      }
    </div>
  `,
})
export class Notifications {
  protected readonly service = inject(NotificationService);
}
