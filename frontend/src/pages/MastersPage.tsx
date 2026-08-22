import { useEffect, useState } from 'react';
import { createProfileMaster, deleteProfileMaster, updateProfileMaster } from '../api/profileMasters';
import { useProfileMasters } from '../hooks/useProfileMasters';
import Button from '../components/ui/Button';
import Card from '../components/ui/Card';
import ConfirmDialog from '../components/ConfirmDialog';
import { Field, Input, TextArea } from '../components/ui/FormField';
import { getErrorMessage } from '../lib/errors';
import type { ProfileMaster } from '../types';

export default function MastersPage() {
  const { masters, error: loadError } = useProfileMasters();
  const [list, setList] = useState<ProfileMaster[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [systemPrompt, setSystemPrompt] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editName, setEditName] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [editSystemPrompt, setEditSystemPrompt] = useState('');
  const [savingEdit, setSavingEdit] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const [deletingMaster, setDeletingMaster] = useState<ProfileMaster | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => setList(masters), [masters]);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !systemPrompt.trim()) return;
    setSaving(true);
    setError(null);
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
    } catch (err) {
      setError(getErrorMessage(err, 'Could not create this Master.'));
    } finally {
      setSaving(false);
    }
  };

  const startEditing = (master: ProfileMaster) => {
    setEditingId(master.id);
    setEditName(master.name);
    setEditDescription(master.description);
    setEditSystemPrompt(master.systemPrompt || '');
    setEditError(null);
  };

  const cancelEditing = () => {
    setEditingId(null);
    setEditError(null);
  };

  const onSaveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (editingId === null || !editName.trim() || !editSystemPrompt.trim()) return;
    setSavingEdit(true);
    setEditError(null);
    try {
      const updated = await updateProfileMaster(editingId, {
        name: editName.trim(),
        description: editDescription.trim(),
        systemPrompt: editSystemPrompt.trim()
      });
      setList((prev) => prev.map((m) => (m.id === editingId ? updated : m)));
      setEditingId(null);
    } catch (err) {
      setEditError(getErrorMessage(err, 'Could not save these changes.'));
    } finally {
      setSavingEdit(false);
    }
  };

  const onConfirmDelete = async () => {
    if (!deletingMaster) return;
    setDeleteError(null);
    try {
      await deleteProfileMaster(deletingMaster.id);
      setList((prev) => prev.filter((m) => m.id !== deletingMaster.id));
      setDeletingMaster(null);
    } catch (err) {
      setDeleteError(getErrorMessage(err, 'Could not delete this Master.'));
    }
  };

  return (
    <div className="mx-auto min-h-screen max-w-4xl px-6 py-12">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Profile Masters</h1>
          <p className="mt-1 text-sm text-booki-muted">
            Masters are the expert personas BooKI can take on during a reading session — they shape tone, vocabulary, and how questions and hints are framed. These are yours alone: editing or deleting one never affects any other user.
          </p>
        </div>
        <Button onClick={() => setShowForm((s) => !s)} className="shrink-0">
          {showForm ? 'Cancel' : '+ New Master'}
        </Button>
      </div>

      {loadError && (
        <div className="mt-4 rounded-lg bg-rose-500/10 px-4 py-3 text-sm text-rose-400 ring-1 ring-rose-500/20">
          {loadError}
        </div>
      )}

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
          {error && <p className="text-sm text-rose-400">{error}</p>}
        </form>
      )}

      <div className="mt-8 grid gap-4 sm:grid-cols-2">
        {list.map((m) =>
          editingId === m.id ? (
            <form
              key={m.id}
              onSubmit={onSaveEdit}
              className="space-y-3 rounded-2xl bg-booki-surface p-6 ring-1 ring-booki-accent/40"
            >
              <Field label="Name">
                <Input value={editName} onChange={(e) => setEditName(e.target.value)} required />
              </Field>
              <Field label="Description">
                <Input value={editDescription} onChange={(e) => setEditDescription(e.target.value)} />
              </Field>
              <Field label="System prompt">
                <TextArea
                  value={editSystemPrompt}
                  onChange={(e) => setEditSystemPrompt(e.target.value)}
                  rows={3}
                  required
                />
              </Field>
              <div className="flex items-center gap-3">
                <Button type="submit" disabled={savingEdit}>
                  {savingEdit ? 'Saving…' : 'Save'}
                </Button>
                <Button type="button" variant="secondary" onClick={cancelEditing}>
                  Cancel
                </Button>
              </div>
              {editError && <p className="text-sm text-rose-400">{editError}</p>}
            </form>
          ) : (
            <Card key={m.id}>
              <div className="flex items-start justify-between gap-2">
                <h3 className="font-bold text-white">{m.name}</h3>
                <div className="flex shrink-0 gap-1">
                  <button
                    onClick={() => startEditing(m)}
                    className="rounded-md px-2 py-1 text-xs text-white/50 hover:bg-white/10 hover:text-white"
                  >
                    Edit
                  </button>
                  <button
                    onClick={() => setDeletingMaster(m)}
                    className="rounded-md px-2 py-1 text-xs text-white/50 hover:bg-rose-500/20 hover:text-rose-400"
                  >
                    Delete
                  </button>
                </div>
              </div>
              <p className="mt-1 text-sm text-booki-muted">{m.description}</p>
              {m.systemPrompt && <p className="mt-3 text-xs italic text-white/40">"{m.systemPrompt}"</p>}
            </Card>
          )
        )}
        {list.length === 0 && <p className="text-sm text-booki-muted">No Masters yet — create one to get started.</p>}
      </div>

      <ConfirmDialog
        open={!!deletingMaster}
        title="Delete this Master?"
        description={
          deletingMaster
            ? `"${deletingMaster.name}" will be removed. Sessions that already used it keep their history.`
            : undefined
        }
        confirmLabel="Delete"
        onConfirm={onConfirmDelete}
        onCancel={() => {
          setDeletingMaster(null);
          setDeleteError(null);
        }}
      />
      {deleteError && <p className="mt-3 text-sm text-rose-400">{deleteError}</p>}
    </div>
  );
}
