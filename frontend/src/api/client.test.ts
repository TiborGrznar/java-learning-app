import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from './client';
import { ApiError } from './errors';
import { resetSessionForTests, useSession } from './session';
import { apiError, authFixture, json, mockFetch } from '../test/fetchMock';

function signedIn(token: string) {
  useSession.setState({ status: 'authenticated', accessToken: token, role: 'STUDENT' });
}

describe('authorized requests', () => {
  beforeEach(() => {
    resetSessionForTests();
    vi.unstubAllGlobals();
  });

  it('sends the access token in the Authorization header', async () => {
    signedIn('t1');
    const { calls } = mockFetch({ 'GET /things': () => json(200, { ok: true }) });

    await api.get('/things');

    expect(calls[0]?.headers['Authorization']).toBe('Bearer t1');
  });

  it('refreshes once after 401 and repeats the request with the new token', async () => {
    signedIn('old');
    let attempts = 0;
    const { calls } = mockFetch({
      'GET /things': (call) => {
        attempts += 1;
        return call.headers['Authorization'] === 'Bearer new'
          ? json(200, { ok: true })
          : apiError(401, 'UNAUTHENTICATED');
      },
      'POST /auth/refresh': () => json(200, authFixture('new')),
    });

    await expect(api.get('/things')).resolves.toEqual({ ok: true });

    expect(attempts).toBe(2);
    expect(calls.filter((c) => c.path === '/auth/refresh')).toHaveLength(1);
    expect(useSession.getState().accessToken).toBe('new');
  });

  it('shares one refresh between parallel requests that got 401', async () => {
    signedIn('old');
    const { calls } = mockFetch({
      'GET /a': (call) =>
        call.headers['Authorization'] === 'Bearer new' ? json(200, 'a') : apiError(401, 'UNAUTHENTICATED'),
      'GET /b': (call) =>
        call.headers['Authorization'] === 'Bearer new' ? json(200, 'b') : apiError(401, 'UNAUTHENTICATED'),
      'POST /auth/refresh': async () => {
        await new Promise((resolve) => setTimeout(resolve, 10));
        return json(200, authFixture('new'));
      },
    });

    const [a, b] = await Promise.all([api.get('/a'), api.get('/b')]);

    expect([a, b]).toEqual(['a', 'b']);
    expect(calls.filter((c) => c.path === '/auth/refresh')).toHaveLength(1);
  });

  it('ends the session when the refresh is rejected', async () => {
    signedIn('old');
    mockFetch({
      'GET /things': () => apiError(401, 'UNAUTHENTICATED'),
      'POST /auth/refresh': () => apiError(401, 'INVALID_REFRESH_TOKEN'),
    });

    await expect(api.get('/things')).rejects.toMatchObject({ code: 'INVALID_REFRESH_TOKEN' });

    expect(useSession.getState().status).toBe('anonymous');
    expect(useSession.getState().accessToken).toBeNull();
  });

  it('keeps the session when the refresh fails only because the server is unreachable', async () => {
    signedIn('old');
    mockFetch({
      'GET /things': () => apiError(401, 'UNAUTHENTICATED'),
      'POST /auth/refresh': () => {
        throw new TypeError('Failed to fetch');
      },
    });

    await expect(api.get('/things')).rejects.toMatchObject({ code: 'NETWORK_ERROR' });

    expect(useSession.getState().status).toBe('authenticated');
  });

  it('does not refresh after 403 and returns the error untouched', async () => {
    signedIn('t1');
    const { calls } = mockFetch({ 'GET /things': () => apiError(403, 'ACCESS_DENIED') });

    await expect(api.get('/things')).rejects.toMatchObject({ status: 403, code: 'ACCESS_DENIED' });

    expect(calls).toHaveLength(1);
  });

  it('does not loop: a second 401 after the refresh ends the session', async () => {
    signedIn('old');
    const { calls } = mockFetch({
      'GET /things': () => apiError(401, 'UNAUTHENTICATED'),
      'POST /auth/refresh': () => json(200, authFixture('new')),
    });

    await expect(api.get('/things')).rejects.toBeInstanceOf(ApiError);

    expect(calls.filter((c) => c.path === '/things')).toHaveLength(2);
    expect(useSession.getState().status).toBe('anonymous');
  });

  it('reads Retry-After of a 429 answer', async () => {
    signedIn('t1');
    mockFetch({
      'GET /things': () =>
        new Response(JSON.stringify({ code: 'RATE_LIMITED', message: 'Moc' }), {
          status: 429,
          headers: { 'Content-Type': 'application/json', 'Retry-After': '12' },
        }),
    });

    await expect(api.get('/things')).rejects.toMatchObject({ status: 429, retryAfterSeconds: 12 });
  });

  it('turns a plain-text error answer (for example the CORS 403) into UNEXPECTED_RESPONSE', async () => {
    signedIn('t1');
    mockFetch({ 'GET /things': () => new Response('Invalid CORS request', { status: 403 }) });

    await expect(api.get('/things')).rejects.toMatchObject({ status: 403, code: 'UNEXPECTED_RESPONSE' });
  });

  it('accepts an empty 204 answer', async () => {
    signedIn('t1');
    mockFetch({ 'DELETE /things/1': () => new Response(null, { status: 204 }) });

    await expect(api.delete('/things/1')).resolves.toBeUndefined();
  });
});