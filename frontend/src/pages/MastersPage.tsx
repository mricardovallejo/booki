import { useEffect, useState } from 'react';
import { createProfileMaster } from '../api/profileMasters';
import { useProfileMasters } from '../hooks/useProfileMasters';
import Button from '../components/ui/Button';
import Card from '../components/ui/Card';
import { Field, Input, TextArea } from '../components/ui/FormField';
import type { ProfileMaster } from '../types';

export default function MastersPage() {
  const masters = useProfileMasters();
  const [list, setList] = useState<ProfileMaster[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [systemPrompt, setSystemPrompt] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => setList(masters), [masters]);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !systemPrompt.trim()) return;
    setSaving(true);
    try {
      const created = await createProfileMaster({
        name: name.trim(),
        description: description.trim(),
        systemPrompt: systemPrompt.trim()
      });
      setList((prev) => [...prev, created]);
      setName('');
      setDescription('');
      setSystemPrompt('');
      setShowForm(false);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="mx-auto min-h-screen max-w-4xl px-6 py-12">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Profile Masters</h1>
          <p className="mt-1 text-sm text-booki-muted">
            Masters are the expert personas BooKI can take on during a reading session — they shape tone, vocabulary, and how questions and hints are framed.
          </p>
        </div>
        <Button onClick={() => setShowForm((s) => !s)} className="shrink-0">
          {showForm ? 'Cancel' : '+ New Master'}
        </Button>
      </div>

      {showForm && (
        <form onSubmit={onSubmit} className="mt-6 space-y-4 rounded-2xl bg-booki-surface p-6">
          <Field label="Name">
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Friendly science teacher"
              required
            />
          </Field>
          <Field label="Description">
            <Input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Short summary shown when picking this Master"
            />
          </Field>
          <Field label="System prompt">
            <TextArea
              value={systemPrompt}
              onChange={(e) => setSystemPrompt(e.target.value)}
              placeholder="Describe how this Master should behave, e.g. 'You are a patient teacher who explains concepts step by step.'"
              rows={3}
              required
            />
          </Field>
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving…' : 'Save Master'}
          </Button>
        </form>
      )}

      <div className="mt-8 grid gap-4 sm:grid-cols-2">
        {list.map((m) => (
          <Card key={m.id}>
            <h3 className="font-bold text-white">{m.name}</h3>
            <p className="mt-1 text-sm text-booki-muted">{m.description}</p>
            {m.systemPrompt && <p className="mt-3 text-xs italic text-white/40">"{m.systemPrompt}"</p>}
          </Card>
        ))}
        {list.length === 0 && <p className="text-sm text-booki-muted">No Masters yet — create one to get started.</p>}
      </div>
    </div>
  );
}
