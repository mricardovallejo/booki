import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { createSession } from '../api/sessions';
import { useAiProfiles } from '../hooks/useAiProfiles';
import { useReaderProfiles } from '../hooks/useReaderProfiles';
import { LANGUAGE_LABELS, getDefaultLanguage, setDefaultLanguage, toSessionLanguage } from '../lib/preferences';
import { ROUTES } from '../config/routes';
import { getErrorMessage } from '../lib/errors';
import Button from './ui/Button';
import { Field, Select } from './ui/FormField';
import type { Difficulty, Document, ReaderLevel, SessionLanguage } from '../types';

interface Props {
  document: Document | null;
  onClose: () => void;
}

const DIFFICULTIES: { value: Difficulty; label: string }[] = [
  { value: 'easy', label: 'Easy' },
  { value: 'medium', label: 'Medium' },
  { value: 'hard', label: 'Advanced' }
];

const LEVEL_TO_DIFFICULTY: Record<ReaderLevel, Difficulty> = {
  beginner: 'easy',
  intermediate: 'medium',
  advanced: 'hard'
};

export default function CreateSessionModal({ document, onClose }: Props) {
  const navigate = useNavigate();
  const { profiles, error: profilesError } = useAiProfiles();
  const { profiles: readerProfiles, error: readerError } = useReaderProfiles();
  const [difficulty, setDifficulty] = useState<Difficulty>('medium');
  const [difficultyTouched, setDifficultyTouched] = useState(false);
  const [aiProfileId, setAiProfileId] = useState<number | undefined>(undefined);
  const [readerProfileId, setReaderProfileId] = useState<number | undefined>(undefined);
  const [language, setLanguage] = useState<SessionLanguage>(getDefaultLanguage());
  const [rememberLanguage, setRememberLanguage] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (document) {
      setLanguage(getDefaultLanguage());
      setRememberLanguage(false);
      setDifficultyTouched(false);
    }
  }, [document]);

  // Preselect the user's default tutor profile / reader profile once the lists load.
  useEffect(() => {
    if (aiProfileId === undefined && profiles.length > 0) {
      setAiProfileId((profiles.find((p) => p.isDefault) ?? profiles[0]).id);
    }
  }, [profiles, aiProfileId]);
  useEffect(() => {
    if (readerProfileId === undefined && readerProfiles.length > 0) {
      setReaderProfileId((readerProfiles.find((r) => r.isDefault) ?? readerProfiles[0]).id);
    }
  }, [readerProfiles, readerProfileId]);

  // Suggest a difficulty from the chosen reader profile's level, unless the
  // reader has already picked one by hand.
  const selectedReader = readerProfiles.find((r) => r.id === readerProfileId);
  useEffect(() => {
    if (!difficultyTouched && selectedReader?.readerLevel) {
      setDifficulty(LEVEL_TO_DIFFICULTY[selectedReader.readerLevel]);
    }
  }, [selectedReader, difficultyTouched]);

  if (!document) return null;

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      if (rememberLanguage) {
        setDefaultLanguage(language);
      }
      // No starting page here on purpose — the session just opens at page 1;
      // the actual range that matters (chat/quiz/summary) is chosen per task,
      // not here.
      const session = await createSession({
        documentId: document.id,
        title: document.title,
        difficulty,
        aiProfileId,
        readerProfileId,
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
          <Field label="Tutor profile — how BooKI teaches">
            <Select
              value={aiProfileId ?? ''}
              onChange={(e) => setAiProfileId(e.target.value ? Number(e.target.value) : undefined)}
            >
              {profiles.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                  {p.isDefault ? ' — default' : ''}
                </option>
              ))}
            </Select>
            {profilesError && <p className="mt-1 text-xs text-rose-400">{profilesError}</p>}
          </Field>

          <Field label="Reader profile — who is reading">
            <Select
              value={readerProfileId ?? ''}
              onChange={(e) => setReaderProfileId(e.target.value ? Number(e.target.value) : undefined)}
            >
              {readerProfiles.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name}
                  {r.isDefault ? ' — default' : ''}
                </option>
              ))}
            </Select>
            {readerError && <p className="mt-1 text-xs text-rose-400">{readerError}</p>}
          </Field>

          <Field label="Difficulty — quizzes, evaluations & reviews">
            <div className="grid grid-cols-3 gap-2">
              {DIFFICULTIES.map((d) => (
                <button
                  key={d.value}
                  type="button"
                  onClick={() => {
                    setDifficulty(d.value);
                    setDifficultyTouched(true);
                  }}
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
            <p className="mt-1 text-xs text-white/50">
              {selectedReader?.readerLevel ? `Preset from ${selectedReader.name}'s starting level. ` : ''}
              Only affects quiz questions, grading, and progress reviews — chat and summaries are not
              affected. Change it anytime during the session.{' '}
              <Link to={`${ROUTES.aiProfiles}?tab=app&key=${difficulty}`} className="text-booki-accent hover:underline">
                What each level means →
              </Link>
            </p>
          </Field>

          <Field label="BooKI's interaction language">
            <Select value={language} onChange={(e) => setLanguage(toSessionLanguage(e.target.value))}>
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
