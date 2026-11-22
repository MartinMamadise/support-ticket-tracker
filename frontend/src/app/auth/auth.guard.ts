import { inject } from '@angular/core';
import { CanActivateFn, ActivatedRouteSnapshot, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';
import { Role } from './auth.model';
import { safeReturnUrl } from './return-url';

/** Protects application routes: unauthenticated users are sent to the login page, remembering where they wanted to go. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth
    .ensureLoaded()
    .pipe(
      map((user) =>
        user ? true : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } }),
      ),
    );
};

/** For the login page: people who are already signed in are sent on to the app. */
export const guestGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth
    .ensureLoaded()
    .pipe(
      map((user) =>
        user ? router.parseUrl(safeReturnUrl(route.queryParamMap.get('returnUrl'))) : true,
      ),
    );
};

/**
 * Only lets accounts with one of the roles through; everyone else goes to the dashboard. This is about navigation
 * only: the backend rejects the same requests with 403 regardless of what the UI allows.
 */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    return auth
      .ensureLoaded()
      .pipe(
        map((user) =>
          user && roles.includes(user.role)
            ? true
            : router.createUrlTree(user ? ['/dashboard'] : ['/login']),
        ),
      );
  };
}

export const staffGuard = roleGuard('ADMIN', 'SUPPORT_AGENT');
export const adminGuard = roleGuard('ADMIN');
