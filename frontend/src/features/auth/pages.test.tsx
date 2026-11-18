import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { resetSessionForTests, useSession } from '../../api/session';
import { apiError, authFixture, json, mockFetch, userFixture } from '../../test/fetchMock';
import { LoginPage } from './LoginPage';
import { RegisterPage } from './RegisterPage';

function renderAt(path: string) {
  const router = createMemoryRouter(
    [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/', element: <p>Domov</p> },
    ],
    { initialEntries: [path] },
  );
  render(<RouterProvider router={router} />);
  return router;
}

function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

describe('LoginPage', () => {
  beforeEach(() => {
    resetSessionForTests();
    vi.unstubAllGlobals();
  });

  it('signs in and goes to the home page', async () => {
    mockFetch({
      'POST /auth/login': () => json(200, authFixture('a1')),
      'GET /users/me': () => json(200, userFixture),
    });
    renderAt('/login');

    fill('E-mail', 'jana@example.com');
    fill('Heslo', 'tajne-heslo');
    fireEvent.click(screen.getByRole('button', { name: 'Prihlásiť sa' }));

    expect(await screen.findByText('Domov')).toBeInTheDocument();
    expect(useSession.getState().status).toBe('authenticated');
  });

  it('shows the message of the server for wrong credentials', async () => {
    mockFetch({ 'POST /auth/login': () => apiError(401, 'INVALID_CREDENTIALS', 'Nesprávny e-mail alebo heslo') });
    renderAt('/login');

    fill('E-mail', 'x@y.sk');
    fill('Heslo', 'zle');
    fireEvent.click(screen.getByRole('button', { name: 'Prihlásiť sa' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Nesprávny e-mail alebo heslo');
  });
});

describe('RegisterPage', () => {
  beforeEach(() => {
    resetSessionForTests();
    vi.unstubAllGlobals();
  });

  it('refuses different passwords without calling the server', () => {
    const { calls } = mockFetch({});
    renderAt('/register');

    fill('Meno', 'Jana');
    fill('Priezvisko', 'Nováková');
    fill('E-mail', 'jana@example.com');
    fill('Heslo', 'dlhe-heslo-1');
    fill('Potvrdenie hesla', 'ine-heslo-2');
    fireEvent.click(screen.getByRole('button', { name: 'Vytvoriť účet' }));

    expect(screen.getByText('Heslá sa nezhodujú')).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it('shows field errors of the server and then registers', async () => {
    let attempt = 0;
    mockFetch({
      'POST /auth/register': () => {
        attempt += 1;
        return attempt === 1
          ? apiError(400, 'VALIDATION_FAILED', 'Neplatné údaje', { fieldErrors: { email: 'E-mail je obsadený' } })
          : json(201);
      },
    });
    const router = renderAt('/register');

    fill('Meno', 'Jana');
    fill('Priezvisko', 'Nováková');
    fill('E-mail', 'jana@example.com');
    fill('Heslo', 'dlhe-heslo-1');
    fill('Potvrdenie hesla', 'dlhe-heslo-1');
    fireEvent.click(screen.getByRole('button', { name: 'Vytvoriť účet' }));

    expect(await screen.findByText('E-mail je obsadený')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Vytvoriť účet' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
  });
});