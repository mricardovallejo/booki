import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { resetPassword } from '../api/auth';
import { ROUTES } from '../config/routes';
import { getErrorMessage } from '../lib/errors';
import AuthCard from '../components/AuthCard';
import Button from '../components/ui/Button';
import { Field, Input } from '../components/ui/FormField';

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (newPassword !== confirmPassword) {
      setError("The passwords don't match.");
      return;
    }
    setLoading(true);
    try {
      await resetPassword(token ?? '', newPassword);
      setDone(true);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not change your password.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthCard>
      <h1 className="mt-4 text-xl font-bold text-white">Choose a new password</h1>
      {done && <p className="mt-4 text-sm text-white/80">Your password was changed. You can sign in now.</p>}
      {!done && !token && (
        <p className="mt-4 text-sm text-rose-400">
          This link is incomplete.{' '}
          <Link to={ROUTES.forgotPassword} className="font-bold text-white hover:text-booki-accent">
            Request a new one
          </Link>
          .
        </p>
      )}
      {!done && token && (
        <form onSubmit={onSubmit} className="mt-6 space-y-4">
          <Field label="New password">
            <Input
              type="password"
              required
              minLength={8}
              autoComplete="new-password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
            <p className="mt-1 text-xs text-white/50">At least 8 characters.</p>
          </Field>
          <Field label="Confirm new password">
            <Input
              type="password"
              required
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />
          </Field>

          {error && <p className="text-sm text-rose-400">{error}</p>}

          <Button type="submit" disabled={loading} className="w-full">
            {loading ? 'One moment…' : 'Change password'}
          </Button>
        </form>
      )}
      <p className="mt-6 text-center text-sm">
        <Link to={ROUTES.login} className="font-bold text-white hover:text-booki-accent">
          {done ? 'Go to sign in' : 'Back to sign in'}
        </Link>
      </p>
    </AuthCard>
  );
}
