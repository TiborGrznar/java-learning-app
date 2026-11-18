import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { ApiError, describeError } from '../../api/errors';
import { signIn } from '../../api/session';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { TextField } from '../../components/TextField';
import { AuthLayout } from './AuthLayout';

interface LocationState {
  from?: string;
  registered?: boolean;
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state ?? {}) as LocationState;

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setSubmitting(true);
    try {
      await signIn({ email: email.trim(), password });
      void navigate(state.from ?? '/', { replace: true });
    } catch (caught) {
      if (caught instanceof ApiError && Object.keys(caught.fieldErrors).length > 0) {
        setFieldErrors(caught.fieldErrors);
      } else {
        setError(describeError(caught));
      }
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout
      title="Prihlásenie"
      subtitle="Pokračujte vo svojom učení Javy."
      footer={
        <>
          Nemáte účet? <Link to="/register">Zaregistrujte sa</Link>
        </>
      }
    >
      <form className="flex flex-col gap-4" onSubmit={(event) => void handleSubmit(event)} noValidate>
        {state.registered && <Alert variant="success">Účet bol vytvorený. Teraz sa môžete prihlásiť.</Alert>}
        {error && <Alert variant="error">{error}</Alert>}
        <TextField
          label="E-mail"
          type="email"
          autoComplete="username"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={fieldErrors['email']}
          required
        />
        <TextField
          label="Heslo"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={fieldErrors['password']}
          required
        />
        <Button type="submit" loading={submitting}>
          Prihlásiť sa
        </Button>
      </form>
    </AuthLayout>
  );
}