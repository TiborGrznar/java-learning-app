import { Navigate, Outlet, useLocation } from 'react-router';
import { retryBootstrap, useSession } from '../../api/session';
import type { UserRole } from '../../api/types';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { ForbiddenPage } from '../errors/ForbiddenPage';

/** Routes for signed-in users, optionally only for some roles. */
export function RequireAuth({ roles }: { roles?: readonly UserRole[] }) {
  const status = useSession((state) => state.status);
  const role = useSession((state) => state.role);
  const location = useLocation();

  if (status === 'booting') return <Spinner label="Načítava sa aplikácia…" />;

  if (status === 'unavailable') {
    return (
      <main className="mx-auto my-24 flex max-w-120 flex-col gap-4 px-4">
        <Alert variant="error">Server je nedostupný, preto sa nedá zistiť, či ste prihlásený.</Alert>
        <Button onClick={() => void retryBootstrap()}>Skúsiť znova</Button>
      </main>
    );
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }

  if (roles && (role === null || !roles.includes(role))) return <ForbiddenPage />;

  return <Outlet />;
}

/** Login and registration: a signed-in user goes to the home page instead. */
export function GuestOnly() {
  const status = useSession((state) => state.status);
  if (status === 'booting') return <Spinner label="Načítava sa aplikácia…" />;
  if (status === 'authenticated') return <Navigate to="/" replace />;
  return <Outlet />;
}