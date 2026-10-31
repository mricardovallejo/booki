import { useState } from 'react';
import { Link } from 'react-router-dom';
import { forgotPassword } from '../api/auth';
import { ROUTES } from '../config/routes';
import { getErrorMessage } from '../lib/errors';
import AuthCard from '../components/AuthCard';
import Button from '../components/ui/Button';
import { Field, Input } from '../components/ui/FormField';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [deliverTo, setDeliverTo] = useState('');
  const [requested, setRequested] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await forgotPassword(email, deliverTo.trim() || undefined);
      setRequested(true);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not send the request. Please try again.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthCard>
      <h1 className="mt-4 text-xl font-bold text-white">Forgot your password?</h1>
      {requested ? (
        // Deliberately the same whether or not a link went out, so this screen
        // can't be used to find out who has an account.
        <p className="mt-4 text-sm text-white/80">
          If that account exists, we've sent a link to choose a new password. Check your inbox and
          spam folder. The link expires in 1 hour.
        </p>
      ) : (
        <form onSubmit={onSubmit} className="mt-6 space-y-4">
          <Field label="Account email">
            <Input
              type="email"
              required
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@email.com"
            />
          </Field>
          <Field label="Send the link to (optional)">
            <Input
              type="email"
              value={deliverTo}
              onChange={(e) => setDeliverTo(e.target.value)}
              placeholder="Leave empty to use your account email"
            />
          </Field>

          {error && <p className="text-sm text-rose-400">{error}</p>}

          <Button type="submit" disabled={loading} className="w-full">
            {loading ? 'One moment…' : 'Send link'}
          </Button>
        </form>
      )}
      <p className="mt-6 text-center text-sm">
        <Link to={ROUTES.login} className="font-bold text-white hover:text-booki-accent">
          Back to sign in
        </Link>
      </p>
    </AuthCard>
  );
}
