import { ErrorPage } from './ErrorPage';

export function ForbiddenPage() {
  return <ErrorPage title="Nemáte prístup" message="Táto stránka je určená pre inú rolu." />;
}