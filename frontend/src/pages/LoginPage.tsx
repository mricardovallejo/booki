import { useState } from 'react';
import { Link, useLocation, useNavigate, type Location } from 'react-router-dom';
import { login as loginRequest, register as registerRequest } from '../api/auth';
import { useAuth } from '../context/AuthContext';
import { ROUTES } from '../config/routes';
import { getErrorMessage } from '../lib/errors';
import AuthCard from '../components/AuthCard';
import Button from '../components/ui/Button';
import { Field, Input } from '../components/ui/FormField';

// Dev-only convenience. `import.meta.env.DEV` is statically false in a
// production build, so Vite tree-shakes the button and these constants out of
// the shipped bundle entirely.
const DEMO_ENABLED = import.meta.env.DEV;
const DEMO_EMAIL = 'demo@booki.app';
const DEMO_PASSWORD = 'password';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: Location } | null)?.from?.pathname || ROUTES.home;

  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const result =
        mode === 'login'
          ? await loginRequest({ email, password })
          : await registerRequest({ email, password, name });
      login(result.token, result.user);
      navigate(from, { replace: true });
    } catch (err: unknown) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const fillDemo = () => {
    setMode('login');
    setEmail(DEMO_EMAIL);
    setPassword(DEMO_PASSWORD);
    setError(null);
  };

  return (
    <AuthCard>
        <h1 className="mt-4 text-xl font-bold text-white">
          {mode === 'login' ? 'Sign in' : 'Create your account'}
        </h1>
        <p className="mt-1 text-sm text-booki-muted">
          {mode === 'login'
            ? 'Sign in to continue your reads and sessions with BooKI.'
            : 'Sign up to start reading and chatting with BooKI.'}
        </p>

        <form onSubmit={onSubmit} className="mt-6 space-y-4">
          {mode === 'register' && (
            <Field label="Name">
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Your name" />
            </Field>
          )}
          <Field label="Email">
            <Input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@email.com"
            />
          </Field>
          <Field label="Password">
            <Input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
            />
            {mode === 'login' && (
              <Link to={ROUTES.forgotPassword} className="mt-1 inline-block text-xs text-white/60 hover:text-booki-accent">
                Forgot your password?
              </Link>
            )}
          </Field>

          {error && <p className="text-sm text-rose-400">{error}</p>}

          <Button type="submit" disabled={loading} className="w-full">
            {loading ? 'One moment…' : mode === 'login' ? 'Sign in' : 'Sign up'}
          </Button>
        </form>

        {DEMO_ENABLED && (
          <Button variant="ghost" onClick={fillDemo} className="mt-4 w-full !text-white/70">
            Use demo account (demo@booki.app / password)
          </Button>
        )}

        <p className="mt-6 text-center text-sm text-booki-muted">
          {mode === 'login' ? "Don't have an account?" : 'Already have an account?'}{' '}
          <button
            type="button"
            onClick={() => {
              setMode(mode === 'login' ? 'register' : 'login');
              setError(null);
            }}
            className="font-bold text-white hover:text-booki-accent"
          >
            {mode === 'login' ? 'Sign up' : 'Sign in'}
          </button>
        </p>
    </AuthCard>
  );
}
