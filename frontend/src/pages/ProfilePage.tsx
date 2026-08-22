import { useEffect, useState } from 'react';
import { useUserProfile } from '../hooks/useUserProfile';
import Button from '../components/ui/Button';
import Card from '../components/ui/Card';
import { Field, Input, TextArea } from '../components/ui/FormField';

export default function ProfilePage() {
  const { user, saving, save } = useUserProfile();
  const [name, setName] = useState('');
  const [bio, setBio] = useState('');
  const [systemPrompt, setSystemPrompt] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (user) {
      setName(user.name);
      setBio(user.bio || '');
      setSystemPrompt(user.systemPrompt || '');
    }
  }, [user]);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaved(false);
    await save({ name, bio, systemPrompt });
    setSaved(true);
  };

  return (
    <div className="mx-auto min-h-screen max-w-2xl px-6 py-12">
      <h1 className="text-2xl font-bold text-white">Your profile</h1>
      <p className="mt-1 text-sm text-booki-muted">
        This context follows you into every session. BooKI combines it with the app's own defaults
        and the Profile Master you pick, so explanations, quizzes, and hints are tailored to you —
        not just to whichever Master is active.
      </p>

      <form onSubmit={onSubmit} className="mt-6 space-y-4 rounded-2xl bg-booki-surface p-6">
        <Field label="Name">
          <Input value={name} onChange={(e) => setName(e.target.value)} required />
        </Field>
        <Field label="Email">
          <Input value={user?.email || ''} disabled className="opacity-60" />
        </Field>
        <Field label="About you">
          <Input
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            placeholder="e.g. Studying for a certification exam"
          />
        </Field>
        <Field label="Tell BooKI how you like to learn">
          <TextArea
            value={systemPrompt}
            onChange={(e) => setSystemPrompt(e.target.value)}
            placeholder="e.g. Keep answers short and use bullet points. I'm a beginner, avoid jargon."
            rows={4}
          />
        </Field>

        <div className="flex items-center gap-3">
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving…' : 'Save profile'}
          </Button>
          {saved && !saving && <span className="text-xs text-emerald-400">Saved</span>}
        </div>
      </form>

      <Card className="mt-6">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-booki-muted">
          How this fits together
        </h3>
        <ul className="mt-2 space-y-1.5 text-sm text-white/70">
          <li>
            <span className="font-semibold text-white">App defaults</span> — BooKI's baseline
            behavior and the session's language.
          </li>
          <li>
            <span className="font-semibold text-white">Profile Master</span> — the expert persona
            picked for that session (tone, vocabulary, difficulty).
          </li>
          <li>
            <span className="font-semibold text-white">Your profile</span> — what you write here,
            applied across every session you create.
          </li>
        </ul>
        <p className="mt-3 text-xs text-booki-muted">
          You can see exactly how these combine for a given session from the "Context" icon in its
          Chat panel.
        </p>
      </Card>
    </div>
  );
}
