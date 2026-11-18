import type { RouteObject } from 'react-router';
import { GuestOnly, RequireAuth } from '../features/auth/guards';
import { LoginPage } from '../features/auth/LoginPage';
import { RegisterPage } from '../features/auth/RegisterPage';
import { NotFoundPage } from '../features/errors/NotFoundPage';
import { HomePage } from '../features/home/HomePage';
import { AppShell } from './AppShell';

/** Kept apart from the router object so tests can build a memory router from the same routes. */
export const routes: RouteObject[] = [
  {
    element: <GuestOnly />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [{ index: true, element: <HomePage /> }],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
];