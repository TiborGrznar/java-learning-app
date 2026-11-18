export interface ApiErrorInit {
  status: number;
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
  retryAfterSeconds?: number;
}

/**
 * An error answer of the API, or a failure to reach it (status 0).
 * `code` is what the logic decides on, `message` is Slovak text for the user.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;
  readonly retryAfterSeconds: number | undefined;

  constructor(init: ApiErrorInit) {
    super(init.message);
    this.name = 'ApiError';
    this.status = init.status;
    this.code = init.code;
    this.fieldErrors = init.fieldErrors ?? {};
    this.retryAfterSeconds = init.retryAfterSeconds;
  }
}

export const NETWORK_ERROR = 'NETWORK_ERROR';
export const UNEXPECTED_RESPONSE = 'UNEXPECTED_RESPONSE';

/** Text shown to the user for any thrown value. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'RATE_LIMITED' || error.status === 429) {
      return error.retryAfterSeconds
        ? `Príliš veľa požiadaviek. Skúste to znova o ${error.retryAfterSeconds} s.`
        : 'Príliš veľa požiadaviek. Skúste to o chvíľu znova.';
    }
    if (error.code === 'ORIGIN_NOT_ALLOWED') {
      return 'Server odmietol adresu aplikácie (chyba konfigurácie). Kontaktujte správcu.';
    }
    return error.message;
  }
  return 'Nastala neočakávaná chyba. Skúste to znova.';
}