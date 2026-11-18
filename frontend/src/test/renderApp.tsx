import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { queryClient } from '../api/queryClient';
import { bootstrapSession } from '../api/session';
import { routes } from '../app/routes';

/** Renders the real routes at a given address and starts the session like App does. */
export function renderApp(initialPath: string) {
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] });
  const view = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  void bootstrapSession();
  return { router, ...view };
}