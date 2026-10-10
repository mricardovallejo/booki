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
  const [defaultReportEmail, setDefaultReportEmail] = useState('');
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (user) {
      setName(user.name);
      setDefaultReportEmail(user.defaultReportEmail || '');
    }
  }, [user]);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaved(false);
    setError(null);
    try {
      await save({ name, defaultReportEmail });
      setSaved(true);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not save your profile.'));
    }
  };

  return (
    <div className="mx-auto min-h-screen max-w-2xl px-6 py-12">
      <h1 className="text-2xl font-bold text-white">Account details</h1>
      <p className="mt-1 text-sm text-booki-muted">Your name and sign-in email.</p>

      <form onSubmit={onSubmit} className="mt-6 space-y-4 rounded-2xl bg-booki-surface p-6">
        <Field label="Name">
          <Input value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} />
        </Field>
        <Field label="Email">
          <Input value={user?.email || ''} disabled className="opacity-60" />
        </Field>
        <Field label="Default recipient(s) for sent reports">
          <Input
            type="text"
            value={defaultReportEmail}
            onChange={(e) => setDefaultReportEmail(e.target.value)}
            placeholder="parent@email.com; tutor@email.com"
          />
          <p className="mt-1 text-xs text-white/50">
            Pre-fills the "email report" box on progress and quiz reports. One address, or several
            separated by ";". Leave empty to use this BooKI instance's own default, if it has one.
          </p>
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
          Your goal, level, and learning preferences live in{' '}
          <Link to={ROUTES.aiProfiles} className="font-semibold text-booki-accent">
            Reading setup
          </Link>{' '}
          as a <span className="font-semibold text-white">reader profile</span>, so you can keep a
          different one per subject you study.
        </p>
      </Card>
    </div>
  );
}
