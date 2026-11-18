import { Outlet, useNavigate } from 'react-router';
import { signOut, useSession } from '../api/session';
import { Button } from '../components/Button';
import { ROLE_LABELS } from '../components/roles';

/** Frame of every signed-in screen: the dark header with the user and the logout. */
export function AppShell() {
  const user = useSession((state) => state.user);
  const navigate = useNavigate();

  async function handleLogout() {
    await signOut();
    void navigate('/login', { replace: true });
  }

  return (
    <div className="min-h-screen">
      <header className="h-14 bg-navy-900 text-white">
        <div className="mx-auto flex h-full max-w-content items-center justify-between gap-4 px-4">
          <span className="font-semibold tracking-tight">Java Learning App</span>
          {user && (
            <div className="flex items-center gap-3 text-sm">
              <span className="hidden sm:inline">
                {user.name} {user.surname}
              </span>
              <span className="rounded-sm bg-navy-700 px-2 py-0.5 text-xs font-semibold text-gray-200">
                {ROLE_LABELS[user.role]}
              </span>
              <Button variant="ghost" onClick={() => void handleLogout()}>
                Odhlásiť sa
              </Button>
            </div>
          )}
        </div>
      </header>
      <main className="mx-auto max-w-content px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}