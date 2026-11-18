import { ApiError, NETWORK_ERROR, UNEXPECTED_RESPONSE } from './errors';
import type { ErrorBody } from './types';

export const API_PREFIX = '/api/v1';

export interface SendOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Access token for the Authorization header, none for public endpoints. */
  token?: string | null;
  signal?: AbortSignal;
}

function isErrorBody(value: unknown): value is ErrorBody {
  if (typeof value !== 'object' || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return typeof candidate['code'] === 'string' && typeof candidate['message'] === 'string';
}

function parseRetryAfter(response: Response): number | undefined {
  const raw = response.headers.get('Retry-After');
  if (raw === null) return undefined;
  const seconds = Number.parseInt(raw, 10);
  return Number.isFinite(seconds) && seconds > 0 ? seconds : undefined;
}

async function toApiError(response: Response): Promise<ApiError> {
  const retryAfterSeconds = parseRetryAfter(response);
  let parsed: unknown;
  try {
    parsed = await response.json();
  } catch {
    // Not JSON: for example the plain-text 403 of the CORS filter, or an error page of a proxy.
    parsed = undefined;
  }
  if (isErrorBody(parsed)) {
    return new ApiError({
      status: response.status,
      code: parsed.code,
      message: parsed.message,
      fieldErrors: parsed.fieldErrors,
      retryAfterSeconds,
    });
  }
  return new ApiError({
    status: response.status,
    code: UNEXPECTED_RESPONSE,
    message: 'Server vrátil neočakávanú odpoveď. Skúste to znova.',
    retryAfterSeconds,
  });
}

/**
 * One HTTP call to the API. Knows nothing about sessions: the access token is passed in.
 * Sends cookies always (`credentials: 'include'`), the refresh token cookie needs it
 * as soon as the frontend and the API are on different origins.
 */
export async function send<T>(path: string, options: SendOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (options.token) headers['Authorization'] = `Bearer ${options.token}`;

  let response: Response;
  try {
    response = await fetch(`${API_PREFIX}${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      credentials: 'include',
      signal: options.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError({
      status: 0,
      code: NETWORK_ERROR,
      message: 'Server je nedostupný. Skontrolujte pripojenie a skúste to znova.',
    });
  }

  if (!response.ok) throw await toApiError(response);

  const text = await response.text();
  return (text === '' ? undefined : JSON.parse(text)) as T;
}