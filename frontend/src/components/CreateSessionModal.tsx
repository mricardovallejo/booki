import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createSession } from '../api/sessions';
import { useProfileMasters } from '../hooks/useProfileMasters';
import { LANGUAGE_LABELS, getDefaultLanguage, setDefaultLanguage } from '../lib/preferences';
import { ROUTES } from '../config/routes';
import { getErrorMessage } from '../lib/errors';
import Button from './ui/Button';
import { Field, Select } from './ui/FormField';
import type { Difficulty, Document, SessionLanguage } from '../types';

interface Props {
  document: Document | null;
  onClose: () => void;
}

const DIFFICULTIES: { value: Difficulty; label: string }[] = [
  { value: 'easy', label: 'Easy' },
  { value: 'medium', label: 'Medium' },
  { value: 'hard', label: 'Advanced' }
];

export default function CreateSessionModal({ document, onClose }: Props) {
  const navigate = useNavigate();
  const { masters, error: mastersError } = useProfileMasters();
  const [startPage, setStartPage] = useState(1);
  const [endPage, setEndPage] = useState(1);
  const [difficulty, setDifficulty] = useState<Difficulty>('medium');
  const [profileMasterId, setProfileMasterId] = useState<number | undefined>(undefined);
  const [language, setLanguage] = useState<SessionLanguage>(getDefaultLanguage());
  const [rememberLanguage, setRememberLanguage] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (document) {
      setStartPage(1);
      setEndPage(document.pageCount);
      setLanguage(getDefaultLanguage());
      setRememberLanguage(false);
    }
  }, [document]);

  if (!document) return null;

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      if (rememberLanguage) {
        setDefaultLanguage(language);
      }
      const session = await createSession({
        documentId: document.id,
        title: `${document.title} (${startPage}-${endPage})`,
        startPage,
        endPage,
        difficulty,
        profileMasterId,
        language
      });
      navigate(ROUTES.session(session.id));
    } catch (err) {
      setError(getErrorMessage(err, 'Could not create this session.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-2xl bg-booki-surface p-6 shadow-2xl">
        <h2 className="text-2xl font-bold text-white">Create session</h2>
        <p className="mt-1 text-sm text-booki-muted">{document.title}</p>

        <form onSubmit={onSubmit} className="mt-6 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Field label="Start page">
              <input
                type="number"
                min={1}
                max={document.pageCount}
                value={startPage}
                onChange={(e) => setStartPage(Number(e.target.value))}
                className="w-full rounded-lg bg-booki-card px-3 py-2 text-white outline-none ring-1 ring-white/10 focus:ring-booki-accent"
              />
            </Field>
            <Field label="End page">
              <input
                type="number"
                min={1}
                max={document.pageCount}
                value={endPage}
                onChange={(e) => setEndPage(Number(e.target.value))}
                className="w-full rounded-lg bg-booki-card px-3 py-2 text-white outline-none ring-1 ring-white/10 focus:ring-booki-accent"
              />
            </Field>
          </div>

          <Field label="Difficulty">
            <div className="grid grid-cols-3 gap-2">
              {DIFFICULTIES.map((d) => (
                <button
                  key={d.value}
                  type="button"
                  onClick={() => setDifficulty(d.value)}
                  className={`rounded-lg py-2 text-xs font-bold transition ${
                    difficulty === d.value
                      ? 'bg-booki-accent text-white'
                      : 'bg-booki-card text-white/70 hover:bg-booki-card-hover'
                  }`}
                >
                  {d.label}
                </button>
              ))}
            </div>
          </Field>

          <Field label="Profile Master">
            <Select
              value={profileMasterId ?? ''}
              onChange={(e) => setProfileMasterId(e.target.value ? Number(e.target.value) : undefined)}
            >
              <option value="">Default</option>
              {masters.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name}
                </option>
              ))}
            </Select>
            {mastersError && <p className="mt-1 text-xs text-rose-400">{mastersError}</p>}
          </Field>

          <Field label="BooKI's interaction language">
            <Select value={language} onChange={(e) => setLanguage(e.target.value as SessionLanguage)}>
              {(Object.keys(LANGUAGE_LABELS) as SessionLanguage[]).map((lang) => (
                <option key={lang} value={lang}>
                  {LANGUAGE_LABELS[lang]}
                </option>
              ))}
            </Select>
            <label className="mt-2 flex items-center gap-2 text-xs text-white/60">
              <input
                type="checkbox"
                checked={rememberLanguage}
                onChange={(e) => setRememberLanguage(e.target.checked)}
                className="rounded border-white/20 bg-booki-card"
              />
              Set as my default language for new sessions
            </label>
          </Field>

          {error && <p className="text-sm text-rose-400">{error}</p>}

          <div className="flex gap-3 pt-2">
            <Button type="button" variant="secondary" onClick={onClose} className="flex-1">
              Cancel
            </Button>
            <Button type="submit" disabled={loading} className="flex-1">
              {loading ? 'Creating…' : 'Start reading'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
