import { vi } from 'vitest';

export interface RecordedCall {
  method: string;
  path: string;
  headers: Record<string, string>;
  body: unknown;
}

type Handler = (call: RecordedCall) => Response | Promise<Response>;

export function json(status: number, body?: unknown, headers: Record<string, string> = {}): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

export function apiError(status: number, code: string, message = 'Chyba', extra: object = {}): Response {
  return json(status, { code, message, ...extra });
}

/**
 * Replaces fetch with a router keyed by "METHOD /path" (without the /api/v1 prefix).
 * A call without a handler fails the test, so a forgotten request is never silent.
 */
export function mockFetch(handlers: Record<string, Handler>) {
  const calls: RecordedCall[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const path = url.replace(/^\/api\/v1/, '');
    const method = (init?.method ?? 'GET').toUpperCase();
    const call: RecordedCall = {
      method,
      path,
      headers: (init?.headers ?? {}) as Record<string, string>,
      body: typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined,
    };
    calls.push(call);
    const handler = handlers[`${method} ${path}`];
    if (!handler) throw new Error(`Unexpected request: ${method} ${path}`);
    return handler(call);
  });
  vi.stubGlobal('fetch', fetchMock);
  return { calls, fetchMock };
}

export const userFixture = {
  id: 7,
  name: 'Jana',
  surname: 'Nováková',
  email: 'jana@example.com',
  role: 'STUDENT',
  schoolId: 1,
  schoolName: 'Gymnázium',
  classId: 3,
  className: '3.A',
  active: true,
  createdAt: '2026-11-01T10:00:00Z',
} as const;

export const authFixture = (token = 'access-1') => ({
  accessToken: token,
  tokenType: 'Bearer',
  expiresInSeconds: 900,
  role: 'STUDENT',
});