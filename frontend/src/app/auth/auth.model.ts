export type Role = 'ADMIN' | 'SUPPORT_AGENT' | 'USER';

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrator',
  SUPPORT_AGENT: 'Support agent',
  USER: 'User',
};

/** The account returned by the backend (`UserResponse`): never contains a password or hash. */
export interface AuthUser {
  id: number;
  username: string;
  email: string;
  fullName?: string | null;
  role: Role;
  enabled?: boolean;
  createdAt?: string;
}

/** Body of `POST /api/auth/login` (`LoginRequest`): the username field also accepts the account's email address. */
export interface LoginCredentials {
  username: string;
  password: string;
}
