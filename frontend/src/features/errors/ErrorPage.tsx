import { Link } from 'react-router';

interface ErrorPageProps {
  title: string;
  message: string;
}

/** A full-page notice with a way back, shared by the "not found" and the "no access" pages. */
export function ErrorPage({ title, message }: ErrorPageProps) {
  return (
    <section className="mx-auto my-16 flex max-w-120 flex-col items-start gap-3 px-4">
      <h1 className="text-xl font-semibold tracking-tight text-ink">{title}</h1>
      <p className="text-ink-muted">{message}</p>
      <Link to="/" className="text-info-700 underline underline-offset-2">
        Späť na úvod
      </Link>
    </section>
  );
}