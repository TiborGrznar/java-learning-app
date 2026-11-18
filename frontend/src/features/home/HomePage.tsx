import { useSession } from '../../api/session';
import { ROLE_LABELS } from '../../components/roles';

export function HomePage() {
  const user = useSession((state) => state.user);
  if (!user) return null;
  return (
    <section className="flex flex-col gap-3">
      <h1 className="text-xl font-semibold tracking-tight text-ink">Vitajte, {user.name}</h1>
      <p className="text-ink-muted">Prihlásený ako {ROLE_LABELS[user.role].toLowerCase()}.</p>
      <div className="mt-4 flex flex-col gap-2 rounded-lg border border-line bg-surface p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-ink">Aplikácia sa buduje</h2>
        <p className="text-ink-muted">
          Prihlásenie a obnova relácie fungujú. Moduly, kvízy a kódovacie úlohy pribudnú v ďalších krokoch.
        </p>
      </div>
    </section>
  );
}