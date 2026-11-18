import type { ReactNode } from 'react';

interface AuthLayoutProps {
  title: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
}

/** Centred card on the dark brand background, shared by the login and the registration. */
export function AuthLayout({ title, subtitle, children, footer }: AuthLayoutProps) {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-6 bg-navy-900 px-4 py-6">
      <div className="text-lg font-semibold tracking-tight text-white">Java Learning App</div>
      <section className="flex w-full max-w-105 flex-col gap-6 rounded-lg bg-surface p-8 shadow-md">
        <header className="flex flex-col gap-1">
          <h1 className="text-xl font-semibold tracking-tight text-ink">{title}</h1>
          {subtitle && <p className="text-sm text-ink-muted">{subtitle}</p>}
        </header>
        {children}
      </section>
      {footer && <div className="text-sm text-gray-300 [&_a]:text-accent-500 [&_a]:underline">{footer}</div>}
    </main>
  );
}