import { QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { createBrowserRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { queryClient } from '../api/queryClient';
import { bootstrapSession } from '../api/session';
import { routes } from './routes';

export function App() {
  const [router] = useState(() => createBrowserRouter(routes));

  // Safe to run twice (React StrictMode in development): the call is shared.
  useEffect(() => {
    void bootstrapSession();
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}