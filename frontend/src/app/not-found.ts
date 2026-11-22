import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink],
  template: `
    <div class="page-header"><h1>Page not found</h1></div>
    <p>The page you requested does not exist.</p>
    <a routerLink="/dashboard" class="btn">Back to the dashboard</a>
  `,
})
export class NotFound {}
