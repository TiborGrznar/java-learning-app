import { create } from 'zustand';
import { queryClient } from './queryClient';
import { ApiError } from './errors';
import { send } from './http';
import type { AuthResponse, LoginRequest, UserResponse, UserRole } from './types';

/**
 * - booting: the first /auth/refresh at startup has not finished yet
 * - authenticated: the access token and the user are known
 * - anonymous: not signed in (a normal state, not an error)
 * - unavailable: the server could not be reached at startup, the session is unknown
 */
export type SessionStatus = 'booting' | 'authenticated' | 'anonymous' | 'unavailable';

interface SessionState {
  status: SessionStatus;
  /** Lives in memory only: never in localStorage, sessionStorage or a cookie. */
  accessToken: string | null;
  role: UserRole | null;
  user: UserResponse | null;
}

const INITIAL: SessionState = { status: 'booting', accessToken: null, role: null, user: null };

export const useSession = create<SessionState>(() => INITIAL);

const LOCK_NAME = 'jla-refresh';

/**
 * A refresh token works once. Two refreshes at the same moment (two tabs, or two requests
 * of one tab) would let one of them fail with 401. The Web Locks API serializes them across
 * the tabs of the browser: the second one waits and then sends the new cookie of the first.
 * Without the API (old browsers, tests) the call runs directly.
 */
function runExclusive<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.locks) {
    return navigator.locks.request(LOCK_NAME, task);
  }
  return task();
}

let refreshInFlight: Promise<string> | null = null;

/** Gets a new access token with the refresh cookie. Parallel callers of one tab share one call. */
export function refreshAccessToken(): Promise<string> {
  refreshInFlight ??= runExclusive(async () => {
    const auth = await send<AuthResponse>('/auth/refresh', { method: 'POST' });
    useSession.setState({ accessToken: auth.accessToken, role: auth.role });
    return auth.accessToken;
  }).finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

function fetchCurrentUser(token: string): Promise<UserResponse> {
  return send<UserResponse>('/users/me', { token });
}

function isRejection(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 401 || error.status === 403);
}

/** Forgets everything about the signed-in user, including cached server data. */
export function endSession(): void {
  useSession.setState({ status: 'anonymous', accessToken: null, role: null, user: null });
  queryClient.clear();
}

let bootstrapPromise: Promise<void> | null = null;

/**
 * Called once when the application starts. The access token is only in memory, so a page load
 * always begins with /auth/refresh. No cookie or an expired one answers 401: the visitor is
 * simply not signed in.
 */
export function bootstrapSession(): Promise<void> {
  bootstrapPromise ??= (async () => {
    try {
      const token = await refreshAccessToken();
      const user = await fetchCurrentUser(token);
      useSession.setState({ status: 'authenticated', user, role: user.role });
    } catch (error) {
      if (isRejection(error)) {
        endSession();
      } else {
        useSession.setState({ status: 'unavailable', accessToken: null, role: null, user: null });
      }
    }
  })();
  return bootstrapPromise;
}

/** Retry after the server was unavailable at startup. */
export function retryBootstrap(): Promise<void> {
  bootstrapPromise = null;
  useSession.setState({ status: 'booting' });
  return bootstrapSession();
}

export async function signIn(credentials: LoginRequest): Promise<void> {
  const auth = await send<AuthResponse>('/auth/login', { method: 'POST', body: credentials });
  try {
    const user = await fetchCurrentUser(auth.accessToken);
    useSession.setState({ status: 'authenticated', accessToken: auth.accessToken, role: user.role, user });
  } catch (error) {
    endSession();
    throw error;
  }
}

/** The server clears the cookie. A failure of the call must not keep the user signed in here. */
export async function signOut(): Promise<void> {
  try {
    await send<void>('/auth/logout', { method: 'POST' });
  } catch {
    // Ignored on purpose: the local session ends either way.
  }
  endSession();
}

/** Resets the module state. Used by tests only. */
export function resetSessionForTests(): void {
  refreshInFlight = null;
  bootstrapPromise = null;
  useSession.setState(INITIAL);
}