import {
  HttpContextToken,
  HttpErrorResponse,
  HttpEvent,
  HttpInterceptorFn,
  HttpRequest,
  HttpXsrfTokenExtractor,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, switchMap, throwError } from 'rxjs';
import { AUTH_URLS, AuthService } from './auth.service';

/** Exact text the backend's `SecurityErrorHandlers` sends for a rejected CSRF token. */
export const CSRF_FAILURE_MESSAGE = 'Invalid or missing CSRF token';
/** The header the backend expects (Spring `CookieCsrfTokenRepository` default, same as Angular's). */
const CSRF_HEADER = 'X-XSRF-TOKEN';

/** Marks a request that has already been repeated once after refreshing the CSRF token (prevents loops). */
const CSRF_RETRIED = new HttpContextToken<boolean>(() => false);

/**
 * - 401 on a protected API call: the session has ended; the user is sent to the login page (once).
 * - 403 "Invalid or missing CSRF token": the CSRF cookie was missing or stale (e.g. removed by a logout in another tab);
 *   a fresh token is fetched and the request is repeated once.
 * Requests outside `/api/` are untouched, and `/api/auth/*` 401s are left to the caller so a wrong password
 * or "not signed in yet" is not mistaken for an expired session.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  const auth = inject(AuthService);
  const xsrf = inject(HttpXsrfTokenExtractor);

  const send = (request: HttpRequest<unknown>): Observable<HttpEvent<unknown>> =>
    next(request).pipe(
      catchError((error: unknown) => {
        if (!(error instanceof HttpErrorResponse)) {
          return throwError(() => error);
        }
        const isAuthEndpoint = request.url.startsWith('/api/auth/');

        if (error.status === 401 && !isAuthEndpoint) {
          auth.sessionExpired();
        } else if (
          error.status === 403 &&
          request.url !== AUTH_URLS.csrf &&
          !request.context.get(CSRF_RETRIED) &&
          isCsrfFailure(error)
        ) {
          return auth.csrf().pipe(
            switchMap(() => {
              const token = xsrf.getToken();
              // Angular's own XSRF step runs before this interceptor, so the repeated request needs the new token set explicitly.
              return send(
                request.clone({
                  context: request.context.set(CSRF_RETRIED, true),
                  ...(token ? { setHeaders: { [CSRF_HEADER]: token } } : {}),
                }),
              );
            }),
          );
        }
        return throwError(() => error);
      }),
    );

  return send(req);
};

function isCsrfFailure(error: HttpErrorResponse): boolean {
  const body = error.error as { message?: unknown } | null;
  return body?.message === CSRF_FAILURE_MESSAGE;
}
