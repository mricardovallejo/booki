import { useState } from 'react';
import { getErrorMessage } from '../lib/errors';
import type { Tag } from '../types';

interface Props {
  tags: Tag[];
  onCreate: (name: string) => Promise<void>;
  onRename: (id: number, name: string) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
}

export default function TagsBar({ tags, onCreate, onRename, onDelete }: Props) {
  const [adding, setAdding] = useState(false);
  const [newName, setNewName] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingName, setEditingName] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submitNew = async () => {
    if (newName.trim()) {
      try {
        await onCreate(newName.trim());
        setError(null);
      } catch (err) {
        setError(getErrorMessage(err, 'Could not create this tag.'));
      }
    }
    setNewName('');
    setAdding(false);
  };

  const submitRename = async (id: number) => {
    if (editingName.trim()) {
      try {
        await onRename(id, editingName.trim());
        setError(null);
      } catch (err) {
        setError(getErrorMessage(err, 'Could not rename this tag.'));
      }
    }
    setEditingId(null);
  };

  const onDeleteTag = async (id: number) => {
    try {
      await onDelete(id);
      setError(null);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not delete this tag.'));
    }
  };

  return (
    <div id="tags" className="mx-auto max-w-7xl scroll-mt-20 px-6 pb-2 pt-6">
      <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-booki-muted">Your tags</h3>
      <div className="flex flex-wrap items-center gap-2">
        {tags.map((tag) =>
          editingId === tag.id ? (
            <input
              key={tag.id}
              autoFocus
              value={editingName}
              onChange={(e) => setEditingName(e.target.value)}
              onBlur={() => submitRename(tag.id)}
              onKeyDown={(e) => e.key === 'Enter' && submitRename(tag.id)}
              className="rounded-full bg-booki-card px-3 py-1.5 text-sm text-white outline-none ring-1 ring-booki-accent"
            />
          ) : (
            <div
              key={tag.id}
              className="group flex items-center gap-1.5 rounded-full bg-booki-card px-3 py-1.5 text-sm text-white/90"
            >
              <button
                onClick={() => {
                  setEditingId(tag.id);
                  setEditingName(tag.name);
                }}
                className="hover:text-white"
                title="Rename tag"
              >
                {tag.name}
              </button>
              <span className="text-[10px] text-white/40">{tag.documentIds.length}</span>
              <button
                onClick={() => onDeleteTag(tag.id)}
                className="text-white/30 opacity-0 transition group-hover:opacity-100 hover:text-rose-400"
                title="Delete tag"
              >
                ×
              </button>
            </div>
          )
        )}

        {adding ? (
          <input
            autoFocus
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            onBlur={submitNew}
            onKeyDown={(e) => e.key === 'Enter' && submitNew()}
            placeholder="Tag name…"
            className="rounded-full bg-booki-card px-3 py-1.5 text-sm text-white outline-none ring-1 ring-booki-accent placeholder-white/40"
          />
        ) : (
          <button
            onClick={() => setAdding(true)}
            className="rounded-full bg-white/5 px-3 py-1.5 text-sm text-white/70 transition hover:bg-white/10 hover:text-white"
          >
            + New tag
          </button>
        )}
      </div>
      {error && <p className="mt-2 text-xs text-rose-400">{error}</p>}
    </div>
  );
}
