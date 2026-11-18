import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { resetSessionForTests, useSession } from '../api/session';
import { apiError, authFixture, json, mockFetch, userFixture } from '../test/fetchMock';
import { renderApp } from '../test/renderApp';

const notSignedIn = { 'POST /auth/refresh': () => apiError(401, 'INVALID_REFRESH_TOKEN') };
const signedIn = {
  'POST /auth/refresh': () => json(200, authFixture('a1')),
  'GET /users/me': () => json(200, userFixture),
};

describe('the application', () => {
  beforeEach(() => {
    resetSessionForTests();
    vi.unstubAllGlobals();
  });

  it('sends a visitor who is not signed in from / to the login', async () => {
    mockFetch(notSignedIn);

    const { router } = renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Prihlásenie' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('lands a user with a valid cookie on the home page, with the header', async () => {
    mockFetch(signedIn);

    renderApp('/');

    expect(await screen.findByRole('heading', { name: /Vitajte, Jana/ })).toBeInTheDocument();
    expect(screen.getByText('Jana Nováková')).toBeInTheDocument();
    expect(screen.getByText('Študent')).toBeInTheDocument();
  });

  it('sends a signed-in user from /login to the home page', async () => {
    mockFetch(signedIn);

    const { router } = renderApp('/login');

    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
  });

  it('shows the not-found page for an unknown address', async () => {
    mockFetch(notSignedIn);

    renderApp('/neexistuje');

    expect(await screen.findByRole('heading', { name: 'Stránka sa nenašla' })).toBeInTheDocument();
  });

  it('signs in through the login form and shows the home page', async () => {
    mockFetch({
      ...notSignedIn,
      'POST /auth/login': () => json(200, authFixture('a9')),
      'GET /users/me': () => json(200, userFixture),
    });

    renderApp('/login');
    fireEvent.change(await screen.findByLabelText('E-mail'), { target: { value: 'jana@example.com' } });
    fireEvent.change(screen.getByLabelText('Heslo'), { target: { value: 'tajne-heslo' } });
    fireEvent.click(screen.getByRole('button', { name: 'Prihlásiť sa' }));

    expect(await screen.findByRole('heading', { name: /Vitajte, Jana/ })).toBeInTheDocument();
  });

  it('logout clears the session and returns to the login', async () => {
    mockFetch({ ...signedIn, 'POST /auth/logout': () => new Response(null, { status: 204 }) });

    const { router } = renderApp('/');
    fireEvent.click(await screen.findByRole('button', { name: 'Odhlásiť sa' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(useSession.getState().accessToken).toBeNull();
  });
});