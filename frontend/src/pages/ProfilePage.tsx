import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useUserProfile } from '../hooks/useUserProfile';
import { ROUTES } from '../config/routes';
import Button from '../components/ui/Button';
import Card from '../components/ui/Card';
import { Field, Input } from '../components/ui/FormField';
import { getErrorMessage } from '../lib/errors';

export default function ProfilePage() {
  const { user, saving, save } = useUserProfile();
  const [name, setName] = useState('');
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (user) setName(user.name);
  }, [user]);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaved(false);
    setError(null);
    try {
      await save({ name });
      setSaved(true);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not save your profile.'));
    }
  };

  return (
    <div className="mx-auto min-h-screen max-w-2xl px-6 py-12">
      <h1 className="text-2xl font-bold text-white">Your profile</h1>
      <p className="mt-1 text-sm text-booki-muted">Your account details.</p>

      <form onSubmit={onSubmit} className="mt-6 space-y-4 rounded-2xl bg-booki-surface p-6">
        <Field label="Name">
          <Input value={name} onChange={(e) => setName(e.target.value)} required />
        </Field>
        <Field label="Email">
          <Input value={user?.email || ''} disabled className="opacity-60" />
        </Field>

        <div className="flex items-center gap-3">
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving…' : 'Save profile'}
          </Button>
          {saved && !saving && <span className="text-xs text-emerald-400">Saved</span>}
        </div>
        {error && <p className="text-sm text-rose-400">{error}</p>}
      </form>

      <Card className="mt-6">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-booki-muted">
          How you like to learn
        </h3>
        <p className="mt-2 text-sm text-white/70">
          Your goal, level, and learning preferences now live inside each{' '}
          <Link to={ROUTES.aiProfiles} className="font-semibold text-booki-accent">
            AI Profile
          </Link>{' '}
          as its <span className="font-semibold text-white">Reader context</span>, so you can keep a
          different setup per subject you study.
        </p>
      </Card>
    </div>
  );
}
