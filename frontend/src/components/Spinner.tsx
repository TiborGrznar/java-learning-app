interface SpinnerProps {
  label?: string;
}

/** A centred loading state for a whole screen or a section. */
export function Spinner({ label = 'Načítava sa…' }: SpinnerProps) {
  return (
    <div className="flex min-h-60 flex-col items-center justify-center gap-3 text-ink-muted" role="status">
      <span className="size-7 animate-spin rounded-full border-3 border-line border-t-primary" aria-hidden="true" />
      <span className="text-sm">{label}</span>
    </div>
  );
}