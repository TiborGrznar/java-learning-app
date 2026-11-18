import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, NETWORK_ERROR, UNEXPECTED_RESPONSE, describeError } from './errors';
import { send } from './http';

function stubFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response);
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('send', () => {
  it('sends JSON, the token and cookies, and returns the parsed body', async () => {
    const fetchMock = stubFetch(jsonResponse(200, { ok: true }));

    const result = await send<{ ok: boolean }>('/things', { method: 'POST', body: { a: 1 }, token: 't1' });

    expect(result).toEqual({ ok: true });
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/things');
    expect(init.method).toBe('POST');
    expect(init.credentials).toBe('include');
    expect(init.body).toBe('{"a":1}');
    expect(init.headers).toMatchObject({ Authorization: 'Bearer t1', 'Content-Type': 'application/json' });
  });

  it('returns undefined for an empty body (204)', async () => {
    stubFetch(new Response(null, { status: 204 }));
    await expect(send('/auth/logout', { method: 'POST' })).resolves.toBeUndefined();
  });

  it('turns an error body into an ApiError with code and field errors', async () => {
    stubFetch(jsonResponse(400, { code: 'VALIDATION_FAILED', message: 'Neplatné údaje', fieldErrors: { email: 'Neplatný' } }));

    const error = await send('/auth/register', { method: 'POST', body: {} }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 400, code: 'VALIDATION_FAILED', fieldErrors: { email: 'Neplatný' } });
  });

  it('reads Retry-After from a 429', async () => {
    stubFetch(jsonResponse(429, { code: 'RATE_LIMITED', message: 'x' }, { 'Retry-After': '30' }));

    const error = await send('/auth/login', { method: 'POST', body: {} }).catch((e: unknown) => e);

    expect(error).toMatchObject({ status: 429, retryAfterSeconds: 30 });
    expect(describeError(error)).toContain('30 s');
  });

  it('reports a non-JSON error (plain-text 403) as UNEXPECTED_RESPONSE', async () => {
    stubFetch(new Response('Invalid CORS request', { status: 403 }));

    const error = await send('/auth/login').catch((e: unknown) => e);

    expect(error).toMatchObject({ status: 403, code: UNEXPECTED_RESPONSE });
  });

  it('reports an unreachable server as NETWORK_ERROR with status 0', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const error = await send('/auth/login').catch((e: unknown) => e);

    expect(error).toMatchObject({ status: 0, code: NETWORK_ERROR });
  });
});