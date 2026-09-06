import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useAiProfiles } from '../hooks/useAiProfiles';
import { useAiProfile } from '../hooks/useAiProfile';
import { useReaderProfiles } from '../hooks/useReaderProfiles';
import { ROUTES } from '../config/routes';
import { getErrorMessage } from '../lib/errors';
import Button from '../components/ui/Button';
import ConfirmDialog from '../components/ConfirmDialog';
import { Field, Input, Select, TextArea } from '../components/ui/FormField';
import type { AiProfileSlot, AiProfileSlotGroup, CapabilityHint, ReaderLevel } from '../types';

const CAPABILITY_LABELS: { hint: CapabilityHint; label: string }[] = [
  { hint: 'quiz', label: 'Quiz' },
  { hint: 'summary', label: 'Summary' },
  { hint: 'explain', label: 'Explain' },
  { hint: 'mnemonic', label: 'Mnemonic' }
];

// All slot groups, always visible — no "Advanced" fold.
const SLOT_GROUPS: AiProfileSlotGroup[] = ['persona', 'difficulty', 'functions', 'routing'];
const GROUP_LABEL: Record<AiProfileSlotGroup, string> = {
  persona: 'Persona',
  difficulty: 'Difficulty levels',
  functions: 'Function prompts',
  routing: 'Capability routing'
};

// The reader profile's starting level is stored as beginner/intermediate/advanced
// but shown with the same Easy/Medium/Advanced words the session and quiz use, so
// the reader isn't juggling two vocabularies for the same three levels.
const READER_LEVELS: (ReaderLevel | '')[] = ['', 'beginner', 'intermediate', 'advanced'];
const LEVEL_LABEL: Record<string, string> = {
  '': 'Not set',
  beginner: 'Easy',
  intermediate: 'Medium',
  advanced: 'Advanced'
};

const LABEL_PREFIX_RE = /^Function — |^Difficulty — /;

type Tab = 'tutor' | 'reader';

export default function AiProfilesPage() {
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const slotParam = searchParams.get('slot');
  const navigate = useNavigate();
  const { profiles, loading: listLoading, error: listError, refresh, duplicate, remove } = useAiProfiles();
  const readers = useReaderProfiles();

  const selectedId = id ? Number(id) : null;
  const [tab, setTab] = useState<Tab>('tutor');

  // Land on a sensible profile — the user's default, else the first.
  useEffect(() => {
    if (selectedId || listLoading || profiles.length === 0) return;
    const fallback = profiles.find((p) => p.isDefault) ?? profiles[0];
    navigate(ROUTES.aiProfile(fallback.id), { replace: true });
  }, [selectedId, listLoading, profiles, navigate]);

  const editor = useAiProfile(selectedId ?? 0, refresh);
  const {
    profile,
    name,
    setName,
    enabledCapabilities,
    toggleCapability,
    draft,
    setSlotDraft,
    saving,
    error,
    isDirty,
    save,
    revertSlot,
    restore
  } = editor;

  // Reader profiles are edited here too, but they belong to no tutor profile — a
  // session picks one. This picks which one you're editing (default: the user's
  // default).
  // Land on one of the user's OWN reader profiles when they have any (their
  // default, else the first editable one) — not the built-in read-only template,
  // which has no Save / Delete and reads as a dead end.
  const defaultReaderId =
    (readers.profiles.find((r) => r.isDefault && !r.readOnly) ??
      readers.profiles.find((r) => !r.readOnly) ??
      readers.profiles.find((r) => r.isDefault) ??
      readers.profiles[0])?.id ??
    null;
  const [editingReaderId, setEditingReaderId] = useState<number | null>(null);
  const effectiveReaderId = editingReaderId ?? defaultReaderId;
  const selectedReader = readers.profiles.find((r) => r.id === effectiveReaderId) ?? null;

  // In-memory draft of the selected reader profile (shared — saved on its own).
  const [readerDraft, setReaderDraft] = useState<{ name: string; readerLevel: ReaderLevel | ''; context: string }>({
    name: '',
    readerLevel: '',
    context: ''
  });
  const [readerSaving, setReaderSaving] = useState(false);
  useEffect(() => {
    if (selectedReader) {
      setReaderDraft({
        name: selectedReader.name,
        readerLevel: selectedReader.readerLevel ?? '',
        context: selectedReader.context
      });
    }
  }, [selectedReader?.id, selectedReader?.updatedAt]); // eslint-disable-line react-hooks/exhaustive-deps

  const readerDirty =
    !!selectedReader &&
    !selectedReader.readOnly &&
    (readerDraft.name.trim() !== selectedReader.name ||
      readerDraft.readerLevel !== (selectedReader.readerLevel ?? '') ||
      readerDraft.context !== selectedReader.context);

  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  useEffect(() => setSelectedKey(null), [selectedId]);

  // Deep link like /ai-profiles/5?slot=rubric_hard preselects that slot.
  useEffect(() => {
    const slot = profile?.slots.find((s) => s.key === slotParam);
    if (slot) {
      setSelectedKey(slot.key);
      setTab('tutor');
    }
  }, [slotParam, profile]);

  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [confirmRestore, setConfirmRestore] = useState(false);
  const [confirmDeleteReader, setConfirmDeleteReader] = useState(false);

  // Guard unsaved edits (tutor profile OR reader profile): warn on tab close,
  // confirm before switching which profile you're editing, or duplicating.
  // Switching between the Tutor / Reader tabs keeps both drafts in memory, so it
  // needs no guard.
  const anyDirty = isDirty || readerDirty;
  const [pendingDiscard, setPendingDiscard] = useState<(() => void) | null>(null);
  const guardDraft = (run: () => void) => {
    if (anyDirty) setPendingDiscard(() => run);
    else run();
  };
  useEffect(() => {
    if (!anyDirty) return;
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [anyDirty]);

  const groupsFor = useCallback(
    (which: AiProfileSlotGroup[]) => {
      const slots = profile?.slots ?? [];
      return which
        .map((group) => ({ group, slots: slots.filter((s) => s.group === group) }))
        .filter((g) => g.slots.length > 0);
    },
    [profile]
  );
  const grouped = useMemo(() => groupsFor(SLOT_GROUPS), [groupsFor]);

  // Default the tutor editor to the persona slot, else the first slot there is.
  const firstSlotKey =
    profile?.slots.find((s) => s.group === 'persona')?.key ?? profile?.slots[0]?.key ?? null;
  const activeKey = selectedKey ?? firstSlotKey;
  const activeSlot: AiProfileSlot | undefined = profile?.slots.find((s) => s.key === activeKey);
  const activeValue = activeSlot ? draft[activeSlot.key] ?? activeSlot.text : '';
  const activeModified = activeSlot ? activeValue !== activeSlot.originalText : false;

  const onDuplicate = async () => {
    if (!selectedId) return;
    setBusy(true);
    setActionError(null);
    try {
      const created = await duplicate(selectedId);
      navigate(ROUTES.aiProfile(created.id));
    } catch (err) {
      setActionError(getErrorMessage(err, 'Could not duplicate this tutor profile.'));
    } finally {
      setBusy(false);
    }
  };

  // A fresh tutor profile: a clean copy of the user's default (there is no
  // blank-template endpoint, so "new" means "start from the default again").
  const onNewProfile = async () => {
    const base = profiles.find((p) => p.isDefault) ?? profiles[0];
    if (!base) return;
    setBusy(true);
    setActionError(null);
    try {
      const created = await duplicate(base.id, 'New tutor profile');
      navigate(ROUTES.aiProfile(created.id));
    } catch (err) {
      setActionError(getErrorMessage(err, 'Could not create a tutor profile.'));
    } finally {
      setBusy(false);
    }
  };

  const onRestore = async () => {
    setConfirmRestore(false);
    setBusy(true);
    setActionError(null);
    try {
      await restore();
    } finally {
      setBusy(false);
    }
  };

  const onDelete = async () => {
    if (!selectedId) return;
    setActionError(null);
    try {
      await remove(selectedId);
      setConfirmDelete(false);
      const next = profiles.find((p) => p.id !== selectedId);
      navigate(next ? ROUTES.aiProfile(next.id) : ROUTES.aiProfiles, { replace: true });
    } catch (err) {
      setActionError(getErrorMessage(err, 'Could not delete this tutor profile.'));
    }
  };

  // A blank editable reader profile (the backend copies the built-in scaffold).
  const onNewReader = async () => {
    setActionError(null);
    try {
      const created = await readers.create({ name: 'My reader profile' });
      setEditingReaderId(created.id);
    } catch (err) {
      setActionError(getErrorMessage(err, 'Could not create a reader profile.'));
    }
  };

  const duplicateReader = async (fromId?: number) => {
    setActionError(null);
    try {
      const base = readers.profiles.find((r) => r.id === fromId);
      const created = await readers.create({
        name: base && !base.readOnly ? `${base.name} (copy)` : 'My reader profile',
        fromId: fromId ?? selectedReader?.id
      });
      setEditingReaderId(created.id);
    } catch (err) {
      setActionError(getErrorMessage(err, 'Could not create a reader profile.'));
    }
  };

  const onSaveReader = async () => {
    if (!selectedReader || !readerDirty) return;
    setReaderSaving(true);
    setActionError(null);
    try {
      await readers.update(selectedReader.id, {
        name: readerDraft.name.trim() || selectedReader.name,
        context: readerDraft.context,
        readerLevel: readerDraft.readerLevel
      });
    } catch (err) {
      setActionError(getErrorMessage(err, 'Could not save the reader profile.'));
    } finally {
      setReaderSaving(false);
    }
  };

  const onDeleteReader = async () => {
    if (!selectedReader) return;
    setConfirmDeleteReader(false);
    setActionError(null);
    try {
      await readers.remove(selectedReader.id);
      setEditingReaderId(null);
    } catch (err) {
      setActionError(getErrorMessage(err, 'Could not delete that reader profile.'));
    }
  };

  const dirtyOrModified = isDirty || (profile?.slots.some((s) => s.modified) ?? false);

  const TABS: { id: Tab; label: string; dirty: boolean }[] = [
    { id: 'tutor', label: 'Tutor profile', dirty: isDirty },
    { id: 'reader', label: 'Reader profile', dirty: readerDirty }
  ];

  return (
    <div className="mx-auto min-h-screen max-w-5xl px-6 py-10">
      <h1 className="text-2xl font-bold text-white">Reading setup</h1>
      <div className="mt-1 flex items-baseline gap-1.5 text-sm text-booki-muted">
        <span>How BooKI teaches (tutor) and who is reading (reader) — a session picks one of each.</span>
        <Explainer label="What a tutor / reader profile is" className="max-w-2xl">
          A <span className="text-white/70">tutor profile</span> is how BooKI teaches — persona,
          difficulty levels, per-function prompts. A <span className="text-white/70">reader profile</span>{' '}
          is who is reading. They're independent; a session picks one of each. BooKI's core (safety,
          grounding, language) is fixed and part of neither — you can see it, not edit it.
        </Explainer>
      </div>

      {listError && <p className="mt-4 text-sm text-rose-400">{listError}</p>}
      {readers.error && <p className="mt-4 text-sm text-rose-400">{readers.error}</p>}
      {actionError && <p className="mt-4 text-sm text-rose-400">{actionError}</p>}

      <div className="mt-6 inline-flex rounded-lg bg-booki-bg/60 p-1 text-xs font-bold">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={`rounded-md px-4 py-1.5 transition ${
              tab === t.id ? 'bg-booki-accent text-white' : 'text-white/60 hover:text-white'
            }`}
          >
            {t.label}
            {t.dirty && (
              <span
                className="ml-1.5 inline-block h-1.5 w-1.5 rounded-full bg-white/80 align-middle"
                title="Unsaved changes"
              />
            )}
          </button>
        ))}
      </div>

      {tab === 'tutor' ? (
        <>
          <div className="mt-6 flex flex-wrap items-end gap-3">
            <div className="min-w-[16rem] flex-1">
              <Field label="Tutor profile">
                <Select
                  value={selectedId ?? ''}
                  onChange={(e) => {
                    const next = Number(e.target.value);
                    if (next) guardDraft(() => navigate(ROUTES.aiProfile(next)));
                  }}
                >
                  {profiles.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                      {p.isDefault ? ' — default' : ''}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>
            <Button
              variant="secondary"
              size="sm"
              disabled={busy || profiles.length === 0}
              onClick={() => guardDraft(onNewProfile)}
            >
              New
            </Button>
            <Button
              variant="secondary"
              size="sm"
              disabled={busy || !selectedId}
              onClick={() => guardDraft(onDuplicate)}
            >
              {busy ? 'Working…' : 'Duplicate'}
            </Button>
            {profile && dirtyOrModified && (
              <Button variant="ghost" size="sm" disabled={busy} onClick={() => setConfirmRestore(true)}>
                Restore to original
              </Button>
            )}
            {profiles.length > 1 && (
              <Button variant="ghost" size="sm" onClick={() => setConfirmDelete(true)}>
                Delete
              </Button>
            )}
          </div>

          {editor.loading || listLoading ? (
            <p className="mt-8 text-sm text-booki-muted">Loading…</p>
          ) : !profile ? (
            <p className="mt-8 text-sm text-booki-muted">Select a tutor profile to view its prompts.</p>
          ) : (
            <>
              {error && <p className="mt-4 text-sm text-rose-400">{error}</p>}

              <div className="mt-6 sm:max-w-sm">
                <Field label="Tutor profile name">
                  <Input value={name} maxLength={120} onChange={(e) => setName(e.target.value)} />
                </Field>
              </div>

              <div className="mt-6 grid gap-6 md:grid-cols-[15rem_1fr]">
                <nav className="space-y-4">
                  {grouped.map(({ group, slots }) => (
                    <SlotGroup
                      key={group}
                      label={GROUP_LABEL[group]}
                      slots={slots}
                      draft={draft}
                      activeKey={activeKey}
                      onSelect={setSelectedKey}
                    />
                  ))}
                </nav>

                <section className="min-w-0">
                  {activeSlot ? (
                    <>
                      <div className="flex items-center justify-between gap-3">
                        <h2 className="text-sm font-bold text-white">{activeSlot.label}</h2>
                        <span
                          className={`rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide ${
                            activeModified ? 'bg-booki-accent/15 text-booki-accent' : 'bg-white/10 text-white/50'
                          }`}
                        >
                          {activeModified ? 'Edited' : 'Original'}
                        </span>
                      </div>

                      {activeSlot.group === 'difficulty' && (
                        <Explainer label="How the difficulty level works">
                          Defines what this level means — question style, scaffolding, grading strictness. A
                          session runs at one level (set at creation, preset from the reader's starting level);
                          the quiz tab can override it per round.
                        </Explainer>
                      )}

                      {activeSlot.group === 'routing' && (
                        <div className="mt-3">
                          <Field label="Capabilities allowed in this profile">
                            <div className="flex flex-wrap gap-2">
                              {CAPABILITY_LABELS.map(({ hint, label }) => {
                                const on = enabledCapabilities.includes(hint);
                                return (
                                  <button
                                    key={hint}
                                    type="button"
                                    onClick={() => toggleCapability(hint)}
                                    className={`rounded-lg px-3 py-1.5 text-xs font-bold transition ${
                                      on
                                        ? 'bg-booki-accent text-white'
                                        : 'bg-booki-bg/60 text-white/50 hover:bg-booki-card-hover'
                                    }`}
                                  >
                                    {on ? '✓ ' : ''}
                                    {label}
                                  </button>
                                );
                              })}
                            </div>
                            <p className="mt-1.5 text-[11px] text-white/40">
                              A capability left off is hidden in the chat and never auto-triggered. The text
                              below only tunes how eagerly BooKI reaches for the ones left on.
                            </p>
                          </Field>
                        </div>
                      )}

                      {activeSlot.lockedPreamble && (
                        <LockedFrame label="Fixed — the app needs this" text={activeSlot.lockedPreamble} />
                      )}

                      <TextArea
                        value={activeValue}
                        onChange={(e) => setSlotDraft(activeSlot.key, e.target.value)}
                        maxLength={8000}
                        rows={8}
                        className="mt-2 font-mono text-[13px] leading-relaxed"
                        placeholder="Prompt text…"
                      />

                      {activeSlot.lockedPostamble && (
                        <LockedFrame label="Fixed — the app needs this" text={activeSlot.lockedPostamble} />
                      )}

                      {activeModified && (
                        <button
                          onClick={() => revertSlot(activeSlot.key)}
                          className="mt-2 text-xs font-medium text-booki-accent hover:underline"
                        >
                          Restore original text for this prompt
                        </button>
                      )}
                    </>
                  ) : null}
                </section>
              </div>

              <Button className="mt-6" onClick={save} disabled={!isDirty || saving}>
                {saving ? 'Saving…' : isDirty ? 'Save changes' : 'Saved'}
              </Button>
            </>
          )}
        </>
      ) : (
        <div className="mt-6 space-y-4">
          {readers.loading ? (
            <p className="text-sm text-booki-muted">Loading…</p>
          ) : (
            <>
              <div className="flex flex-wrap items-end gap-3">
                <div className="min-w-[16rem] flex-1">
                  <Field label="Reader profile">
                    <Select
                      value={effectiveReaderId ?? ''}
                      onChange={(e) => {
                        const next = Number(e.target.value);
                        guardDraft(() => setEditingReaderId(next));
                      }}
                    >
                      {readers.profiles.map((r) => (
                        <option key={r.id} value={r.id}>
                          {r.name}
                          {r.isDefault ? ' — default' : ''}
                        </option>
                      ))}
                    </Select>
                  </Field>
                </div>
                <Button variant="secondary" size="sm" onClick={() => guardDraft(onNewReader)}>
                  New
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={!selectedReader}
                  onClick={() => guardDraft(() => duplicateReader(selectedReader?.id))}
                >
                  Duplicate
                </Button>
                {selectedReader && !selectedReader.readOnly && (
                  <Button variant="ghost" size="sm" onClick={() => setConfirmDeleteReader(true)}>
                    Delete
                  </Button>
                )}
              </div>

              {selectedReader && selectedReader.readOnly && (
                <div className="rounded-xl bg-white/[0.04] p-4 ring-1 ring-white/10">
                  <p className="text-sm font-semibold text-white">
                    "{selectedReader.name}" is the built-in template — read-only
                  </p>
                  <p className="mt-1 text-xs text-white/50">
                    Renaming, the starting level, the context and Delete only work on your own reader
                    profiles. Use <span className="font-semibold text-white">New</span> or{' '}
                    <span className="font-semibold text-white">Duplicate</span> above to make one — it
                    becomes your default, and Save / Delete show up here.
                  </p>
                </div>
              )}

              {selectedReader && !selectedReader.readOnly && (
                <>
                  <div className="sm:max-w-sm">
                    <Field label="Name">
                      <Input
                        value={readerDraft.name}
                        maxLength={120}
                        onChange={(e) => setReaderDraft((d) => ({ ...d, name: e.target.value }))}
                      />
                    </Field>
                  </div>

                  <div className="sm:max-w-[12rem]">
                    <Field label="Starting level">
                      <Select
                        value={readerDraft.readerLevel}
                        onChange={(e) =>
                          setReaderDraft((d) => ({ ...d, readerLevel: e.target.value as ReaderLevel | '' }))
                        }
                      >
                        {READER_LEVELS.map((lvl) => (
                          <option key={lvl} value={lvl}>
                            {LEVEL_LABEL[lvl]}
                          </option>
                        ))}
                      </Select>
                    </Field>
                  </div>

                  <Explainer label="How the starting level works">
                    Only a starting point: a new session with this reader is preset to this level. You can
                    change it per session, and again per quiz round. What each level <em>means</em> is set
                    in the tutor profile's <span className="text-white/60">Difficulty levels</span>.
                  </Explainer>

                  <TextArea
                    value={readerDraft.context}
                    maxLength={4000}
                    onChange={(e) => setReaderDraft((d) => ({ ...d, context: e.target.value }))}
                    rows={9}
                    className="font-mono text-[13px] leading-relaxed"
                    placeholder={
                      'Describe yourself for this reading: what you want out of it, how familiar you ' +
                      'already are with the topic, how you learn best (worked examples, plain definitions, ' +
                      'analogies, a slower pace), and anything that helps you follow along (short paragraphs, ' +
                      'no jargon, dyslexia-friendly formatting).'
                    }
                  />

                  <Button onClick={onSaveReader} disabled={!readerDirty || readerSaving}>
                    {readerSaving ? 'Saving…' : readerDirty ? 'Save changes' : 'Saved'}
                  </Button>
                </>
              )}
            </>
          )}
        </div>
      )}

      <ConfirmDialog
        open={confirmDelete}
        title="Delete this tutor profile?"
        description={
          profile ? `"${profile.name}" will be removed. Sessions that already used it keep their history.` : undefined
        }
        confirmLabel="Delete"
        onConfirm={onDelete}
        onCancel={() => setConfirmDelete(false)}
      />

      <ConfirmDialog
        open={confirmRestore}
        title="Restore to original?"
        description={
          profile
            ? `Every prompt and the capabilities of "${profile.name}" go back to how it shipped. Its name stays.`
            : undefined
        }
        confirmLabel="Restore"
        onConfirm={onRestore}
        onCancel={() => setConfirmRestore(false)}
      />

      <ConfirmDialog
        open={confirmDeleteReader}
        title="Delete this reader profile?"
        description={
          selectedReader
            ? `"${selectedReader.name}" will be removed. Sessions that used it fall back to your default reader.`
            : undefined
        }
        confirmLabel="Delete"
        onConfirm={onDeleteReader}
        onCancel={() => setConfirmDeleteReader(false)}
      />

      <ConfirmDialog
        open={!!pendingDiscard}
        title="Discard unsaved changes?"
        description="You have unsaved edits on a tutor profile or a reader profile. Leaving now loses them."
        confirmLabel="Discard"
        onConfirm={() => {
          const run = pendingDiscard;
          setPendingDiscard(null);
          run?.();
        }}
        onCancel={() => setPendingDiscard(null)}
      />
    </div>
  );
}

// A discreet disclosure: a small "?" badge that expands the detail inline.
// Closed by default; keeps the page uncluttered for people who already know.
function Explainer({
  children,
  label = 'More info',
  className = ''
}: {
  children: ReactNode;
  label?: string;
  className?: string;
}) {
  return (
    <details className={`text-[11px] text-white/40 ${className}`}>
      <summary
        title={label}
        aria-label={label}
        className="inline-flex h-4 w-4 cursor-pointer list-none items-center justify-center rounded-full bg-white/10 text-[10px] font-bold text-white/50 transition hover:bg-white/20 hover:text-white [&::-webkit-details-marker]:hidden"
      >
        ?
      </summary>
      <div className="mt-1.5 leading-relaxed">{children}</div>
    </details>
  );
}

function SlotGroup({
  label,
  slots,
  draft,
  activeKey,
  onSelect
}: {
  label: string;
  slots: AiProfileSlot[];
  draft: Record<string, string>;
  activeKey: string | null;
  onSelect: (key: string) => void;
}) {
  return (
    <div>
      <p className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-booki-muted">{label}</p>
      <ul className="space-y-0.5">
        {slots.map((slot) => {
          const modified = (draft[slot.key] ?? slot.text) !== slot.originalText;
          return (
            <li key={slot.key}>
              <button
                onClick={() => onSelect(slot.key)}
                className={`flex w-full items-center justify-between gap-2 rounded-md px-2 py-1.5 text-left text-xs transition ${
                  slot.key === activeKey
                    ? 'bg-booki-accent/15 text-white'
                    : 'text-white/70 hover:bg-white/5 hover:text-white'
                }`}
              >
                <span>{slot.label.replace(LABEL_PREFIX_RE, '')}</span>
                {modified && (
                  <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-booki-accent" title="Edited" />
                )}
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function LockedFrame({ label, text }: { label: string; text: string }) {
  return (
    <div className="mt-2 rounded-lg bg-white/[0.03] px-3 py-2 ring-1 ring-white/10">
      <p className="text-[10px] font-semibold uppercase tracking-wide text-white/30">{label}</p>
      <pre className="mt-1 whitespace-pre-wrap font-mono text-[12px] leading-relaxed text-white/40">{text}</pre>
    </div>
  );
}
