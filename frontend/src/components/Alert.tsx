import type { ReactNode } from 'react';

type Variant = 'error' | 'success' | 'info' | 'warning';

// Tailwind finds classes by reading the source text, so every class must be written out in full
// here. A class built from pieces (`border-${color}`) would never reach the stylesheet.
const VARIANT_CLASSES: Record<Variant, string> = {
  error: 'border-danger-700 bg-danger-100 text-danger-700',
  success: 'border-success-700 bg-success-100 text-success-700',
  info: 'border-info-700 bg-info-100 text-info-700',
  warning: 'border-warning-700 bg-warning-100 text-warning-700',
};

interface AlertProps {
  variant?: Variant;
  children: ReactNode;
}

/** Errors are announced at once (role alert), the other variants politely (role status). */
export function Alert({ variant = 'info', children }: AlertProps) {
  return (
    <div
      className={`rounded-md border px-4 py-3 text-sm ${VARIANT_CLASSES[variant]}`}
      role={variant === 'error' ? 'alert' : 'status'}
    >
      {children}
    </div>
  );
}