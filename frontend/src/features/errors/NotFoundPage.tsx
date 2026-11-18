import { ErrorPage } from './ErrorPage';

export function NotFoundPage() {
  return <ErrorPage title="Stránka sa nenašla" message="Adresa neexistuje alebo bola presunutá." />;
}