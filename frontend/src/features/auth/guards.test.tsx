import { fireEvent, render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { resetSessionForTests, useSession } from '../../api/session';
import { apiError, mockFetch } from '../../test/fetchMock';
import { GuestOnly, RequireAuth } from './guards';

function renderAt(path: string) {
  const router = createMemoryRouter(
    [
      { element: <GuestOnly />, children: [{ path: '/login', element: <p>Prihlasovacia stránka</p> }] },
      { element: <RequireAuth />, children: [{ path: '/', element: <p>Domov</p> }] },
      { element: <RequireAuth roles={['ADMIN']} />, children: [{ path: '/admin', element: <p>Správa</p> }] },
    ],
    { initialEntries: [path] },
  );
  render(<RouterProvider router={router} />);
  return router;
}

describe('guards', () => {
  beforeEach(() => {
    resetSessionForTests();
    vi.unstubAllGlobals();
  });

  it('shows a spinner while the session is being restored', () => {
    renderAt('/');
    expect(screen.getByRole('status')).toHaveTextContent('Načítava sa aplikácia…');
  });

  it('sends an anonymous visitor to the login and remembers where they wanted to go', () => {
    useSession.setState({ status: 'anonymous' });
    const router = renderAt('/admin');

    expect(screen.getByText('Prihlasovacia stránka')).toBeInTheDocument();
    expect(router.state.location.state).toEqual({ from: '/admin' });
  });

  it('lets a signed-in user in', () => {
    useSession.setState({ status: 'authenticated', role: 'STUDENT' });
    renderAt('/');
    expect(screen.getByText('Domov')).toBeInTheDocument();
  });

  it('shows "no access" for a role that is not allowed', () => {
    useSession.setState({ status: 'authenticated', role: 'STUDENT' });
    renderAt('/admin');

    expect(screen.getByRole('heading', { name: 'Nemáte prístup' })).toBeInTheDocument();
    expect(screen.queryByText('Správa')).not.toBeInTheDocument();
  });

  it('lets the allowed role in', () => {
    useSession.setState({ status: 'authenticated', role: 'ADMIN' });
    renderAt('/admin');
    expect(screen.getByText('Správa')).toBeInTheDocument();
  });

  it('keeps a signed-in user away from the login page', () => {
    useSession.setState({ status: 'authenticated', role: 'STUDENT' });
    renderAt('/login');
    expect(screen.getByText('Domov')).toBeInTheDocument();
  });

  it('offers a retry when the server was unreachable at startup', async () => {
    useSession.setState({ status: 'unavailable' });
    mockFetch({ 'POST /auth/refresh': () => apiError(401, 'INVALID_REFRESH_TOKEN') });
    renderAt('/');

    expect(screen.getByRole('alert')).toHaveTextContent('Server je nedostupný');
    fireEvent.click(screen.getByRole('button', { name: 'Skúsiť znova' }));

    expect(await screen.findByText('Prihlasovacia stránka')).toBeInTheDocument();
  });
});