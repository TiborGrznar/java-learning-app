import { ApiError } from './errors';
import { send, type SendOptions } from './http';
import { endSession, refreshAccessToken, useSession } from './session';

export type RequestOptions = Omit<SendOptions, 'token'>;

/**
 * Call of an endpoint that needs the access token.
 *
 * Contract with the backend: 401 UNAUTHENTICATED means "try /auth/refresh once". If the
 * refresh fails, the session ends and the router sends the user to the login. 403 and the
 * other errors are returned to the caller untouched.
 */
export async function authorized<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const tokenUsed = useSession.getState().accessToken;
  try {
    return await send<T>(path, { ...options, token: tokenUsed });
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;

    // Another request may have refreshed the token while this one was on its way.
    const current = useSession.getState().accessToken;
    let token: string;
    if (current !== null && current !== tokenUsed) {
      token = current;
    } else {
      try {
        token = await refreshAccessToken();
      } catch (refreshError) {
        if (refreshError instanceof ApiError && (refreshError.status === 401 || refreshError.status === 403)) {
          endSession();
        }
        throw refreshError;
      }
    }

    try {
      return await send<T>(path, { ...options, token });
    } catch (retryError) {
      if (retryError instanceof ApiError && retryError.status === 401) endSession();
      throw retryError;
    }
  }
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) => authorized<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    authorized<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    authorized<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    authorized<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: RequestOptions) => authorized<T>(path, { ...options, method: 'DELETE' }),
};