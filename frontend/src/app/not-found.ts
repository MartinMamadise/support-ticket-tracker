import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink],
  template: `
    <div class="page-header"><h1>Page not found</h1></div>
    <p>The page you requested does not exist.</p>
    <a routerLink="/tickets" class="btn">Back to tickets</a>
  `,
})
export class NotFound {}
