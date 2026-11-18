import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bootstrapSession, refreshAccessToken, resetSessionForTests, signIn, signOut, useSession } from './session';
import { apiError, authFixture, json, mockFetch, userFixture } from '../test/fetchMock';

describe('session', () => {
  beforeEach(() => {
    resetSessionForTests();
    vi.unstubAllGlobals();
  });

  it('bootstrap: a valid cookie signs the user in', async () => {
    mockFetch({
      'POST /auth/refresh': () => json(200, authFixture('a1')),
      'GET /users/me': (call) => {
        expect(call.headers['Authorization']).toBe('Bearer a1');
        return json(200, userFixture);
      },
    });

    await bootstrapSession();

    const state = useSession.getState();
    expect(state.status).toBe('authenticated');
    expect(state.accessToken).toBe('a1');
    expect(state.user?.email).toBe('jana@example.com');
  });

  it('bootstrap: no cookie is a normal "not signed in", not an error', async () => {
    mockFetch({ 'POST /auth/refresh': () => apiError(401, 'INVALID_REFRESH_TOKEN') });

    await bootstrapSession();

    expect(useSession.getState().status).toBe('anonymous');
  });

  it('bootstrap: an unreachable server is "unavailable", not "anonymous"', async () => {
    mockFetch({
      'POST /auth/refresh': () => {
        throw new TypeError('Failed to fetch');
      },
    });

    await bootstrapSession();

    expect(useSession.getState().status).toBe('unavailable');
  });

  it('bootstrap runs once even when called twice (React StrictMode)', async () => {
    const { calls } = mockFetch({
      'POST /auth/refresh': () => apiError(401, 'INVALID_REFRESH_TOKEN'),
    });

    await Promise.all([bootstrapSession(), bootstrapSession()]);

    expect(calls).toHaveLength(1);
  });

  it('parallel refreshes of one tab share one request', async () => {
    const { calls } = mockFetch({ 'POST /auth/refresh': () => json(200, authFixture('a2')) });

    const [first, second] = await Promise.all([refreshAccessToken(), refreshAccessToken()]);

    expect(first).toBe('a2');
    expect(second).toBe('a2');
    expect(calls).toHaveLength(1);
  });

  it('refresh goes through the Web Locks API when the browser has it', async () => {
    const request = vi.fn((_name: string, task: () => Promise<string>) => task());
    vi.stubGlobal('navigator', { ...navigator, locks: { request } });
    mockFetch({ 'POST /auth/refresh': () => json(200, authFixture('a3')) });

    await refreshAccessToken();

    expect(request).toHaveBeenCalledWith('jla-refresh', expect.any(Function));
  });

  it('signIn stores the token in memory and loads the user', async () => {
    const { calls } = mockFetch({
      'POST /auth/login': () => json(200, authFixture('a4')),
      'GET /users/me': () => json(200, userFixture),
    });

    await signIn({ email: 'jana@example.com', password: 'tajne-heslo' });

    expect(useSession.getState().status).toBe('authenticated');
    expect(calls[0]?.body).toEqual({ email: 'jana@example.com', password: 'tajne-heslo' });
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it('signIn: wrong credentials leave the user signed out and rethrow', async () => {
    mockFetch({ 'POST /auth/login': () => apiError(401, 'INVALID_CREDENTIALS', 'Nesprávny e-mail alebo heslo') });

    await expect(signIn({ email: 'x@y.sk', password: 'zle' })).rejects.toMatchObject({
      code: 'INVALID_CREDENTIALS',
    });
    expect(useSession.getState().accessToken).toBeNull();
  });

  it('signOut ends the session even when the server call fails', async () => {
    useSession.setState({ status: 'authenticated', accessToken: 't', role: 'STUDENT' });
    mockFetch({
      'POST /auth/logout': () => {
        throw new TypeError('Failed to fetch');
      },
    });

    await signOut();

    expect(useSession.getState().status).toBe('anonymous');
    expect(useSession.getState().accessToken).toBeNull();
  });
});