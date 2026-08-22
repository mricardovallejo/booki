import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { login as loginRequest, register as registerRequest } from '../api/auth';
import { useAuth } from '../context/AuthContext';
import { ROUTES } from '../config/routes';
import Button from '../components/ui/Button';
import { Field, Input } from '../components/ui/FormField';
import Logo from '../components/Logo';

const DEMO_EMAIL = 'demo@booki.app';
const DEMO_PASSWORD = 'password';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: Location })?.from?.pathname || ROUTES.home;

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
      const message =
        (err as { response?: { data?: { error?: string } } })?.response?.data?.error ||
        'Could not complete the request.';
      setError(message);
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
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-booki-bg px-4">
      <div className="absolute inset-0 bg-gradient-to-br from-indigo-950 via-slate-900 to-black" />
      <div className="absolute inset-0 bg-[url('https://images.unsplash.com/photo-1507842217121-9e9f147d7121?auto=format&fit=crop&w=1600&q=80')] bg-cover bg-center opacity-10" />
      <div className="absolute inset-0 bg-gradient-to-t from-booki-bg via-booki-bg/60 to-transparent" />

      <div className="relative w-full max-w-md rounded-2xl bg-booki-surface/90 p-8 shadow-2xl backdrop-blur">
        <Logo size="lg" />
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
          </Field>

          {error && <p className="text-sm text-rose-400">{error}</p>}

          <Button type="submit" disabled={loading} className="w-full">
            {loading ? 'One moment…' : mode === 'login' ? 'Sign in' : 'Sign up'}
          </Button>
        </form>

        <Button variant="ghost" onClick={fillDemo} className="mt-4 w-full !text-white/70">
          Use demo account (demo@booki.app / password)
        </Button>

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
      </div>
    </div>
  );
}
