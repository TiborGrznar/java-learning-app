import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiError, describeError } from '../../api/errors';
import { send } from '../../api/http';
import type { RegisterRequest } from '../../api/types';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { TextField } from '../../components/TextField';
import { AuthLayout } from './AuthLayout';

export function RegisterPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState<RegisterRequest>({
    name: '',
    surname: '',
    email: '',
    password: '',
    confirmPassword: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  function update<K extends keyof RegisterRequest>(key: K, value: string) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);

    // Quick check on the client. The server repeats every check and decides.
    if (form.password !== form.confirmPassword) {
      setFieldErrors({ confirmPassword: 'Heslá sa nezhodujú' });
      return;
    }

    setFieldErrors({});
    setSubmitting(true);
    try {
      await send<void>('/auth/register', {
        method: 'POST',
        body: { ...form, name: form.name.trim(), surname: form.surname.trim(), email: form.email.trim() },
      });
      void navigate('/login', { replace: true, state: { registered: true } });
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
      title="Vytvoriť účet"
      subtitle="Registrácia študenta. Triedu si pridáte kódom od učiteľa."
      footer={
        <>
          Už máte účet? <Link to="/login">Prihláste sa</Link>
        </>
      }
    >
      <form className="flex flex-col gap-4" onSubmit={(event) => void handleSubmit(event)} noValidate>
        {error && <Alert variant="error">{error}</Alert>}
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <TextField
            label="Meno"
            autoComplete="given-name"
            value={form.name}
            onChange={(event) => update('name', event.target.value)}
            error={fieldErrors['name']}
            required
          />
          <TextField
            label="Priezvisko"
            autoComplete="family-name"
            value={form.surname}
            onChange={(event) => update('surname', event.target.value)}
            error={fieldErrors['surname']}
            required
          />
        </div>
        <TextField
          label="E-mail"
          type="email"
          autoComplete="username"
          value={form.email}
          onChange={(event) => update('email', event.target.value)}
          error={fieldErrors['email']}
          required
        />
        <TextField
          label="Heslo"
          type="password"
          autoComplete="new-password"
          hint="Aspoň 8 znakov."
          value={form.password}
          onChange={(event) => update('password', event.target.value)}
          error={fieldErrors['password']}
          required
        />
        <TextField
          label="Potvrdenie hesla"
          type="password"
          autoComplete="new-password"
          value={form.confirmPassword}
          onChange={(event) => update('confirmPassword', event.target.value)}
          error={fieldErrors['confirmPassword']}
          required
        />
        <Button type="submit" loading={submitting}>
          Vytvoriť účet
        </Button>
      </form>
    </AuthLayout>
  );
}