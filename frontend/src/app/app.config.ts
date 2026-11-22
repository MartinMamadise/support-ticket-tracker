import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { authInterceptor } from './auth/auth.interceptor';
import { AuthService } from './auth/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Angular's built-in XSRF handling (XSRF-TOKEN cookie -> X-XSRF-TOKEN header) matches the backend and stays on.
    provideHttpClient(withInterceptors([authInterceptor])),
    // Find out who is signed in before the first render, so the header and routes do not flicker.
    provideAppInitializer(() => firstValueFrom(inject(AuthService).loadCurrentUser())),
  ],
};
