import { useCallback, useEffect, useMemo, useState } from 'react';
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

// All slot groups, always visible — no "Advanced" fold. The reader profile is
// handled separately (READER_KEY), it's no longer a slot.
const SLOT_GROUPS: AiProfileSlotGroup[] = ['persona', 'difficulty', 'functions', 'routing'];
const GROUP_LABEL: Record<AiProfileSlotGroup, string> = {
  persona: 'Master persona',
  difficulty: 'Difficulty levels',
  functions: 'Function prompts',
  routing: 'Capability routing'
};

const READER_KEY = '__reader__';
const NEW_READER = '__new__';
const READER_LEVELS: (ReaderLevel | '')[] = ['', 'beginner', 'intermediate', 'advanced'];
const LEVEL_LABEL: Record<string, string> = {
  '': 'Not set',
  beginner: 'Beginner',
  intermediate: 'Intermediate',
  advanced: 'Advanced'
};

const LABEL_PREFIX_RE = /^Function — |^Difficulty — /;

export default function AiProfilesPage() {
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const slotParam = searchParams.get('slot');
  const navigate = useNavigate();
  const { profiles, loading: listLoading, error: listError, refresh, duplicate, remove } = useAiProfiles();
  const readers = useReaderProfiles();

  const selectedId = id ? Number(id) : null;

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

  // Reader profiles are edited here too, but they belong to no AI Profile — a
  // session picks one. This picks which one you're editing (default: the user's
  // default).
  const defaultReaderId = (readers.profiles.find((r) => r.isDefault) ?? readers.profiles[0])?.id ?? null;
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
    if (slot) setSelectedKey(slot.key);
  }, [slotParam, profile]);

  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [confirmRestore, setConfirmRestore] = useState(false);

  // Guard unsaved edits (AI Profile OR reader profile): warn on tab close,
  // confirm before switching profile or duplicating.
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

  const activeKey = selectedKey ?? READER_KEY;
  const showReader = activeKey === READER_KEY;
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
      setActionError(getErrorMessage(err, 'Could not duplicate this profile.'));
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
      setActionError(getErrorMessage(err, 'Could not delete this profile.'));
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

  const onPickReader = async (value: string) => {
    setActionError(null);
    if (value === NEW_READER) {
      await duplicateReader(selectedReader?.id);
      return;
    }
    setEditingReaderId(Number(value));
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

  const dirtyOrModified = isDirty || (profile?.slots.some((s) => s.modified) ?? false);

  return (
    <div className="mx-auto min-h-screen max-w-5xl px-6 py-10">
      <h1 className="text-2xl font-bold text-white">Profiles</h1>
      <p className="mt-1 max-w-2xl text-sm text-booki-muted">
        Edit both here. An <span className="font-semibold text-white">AI Profile</span> is the "master"
        — persona, difficulty levels, per-function prompts. A{' '}
        <span className="font-semibold text-white">reader profile</span> is who is reading. They're
        independent; a session picks one of each. The BooKI core stays fixed and is part of neither.
      </p>

      {listError && <p className="mt-4 text-sm text-rose-400">{listError}</p>}
      {readers.error && <p className="mt-4 text-sm text-rose-400">{readers.error}</p>}
      {actionError && <p className="mt-4 text-sm text-rose-400">{actionError}</p>}

      <div className="mt-6 flex flex-wrap items-end gap-3">
        <div className="min-w-[16rem] flex-1">
          <Field label="AI Profile">
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
        <Button variant="secondary" size="sm" disabled={busy || !selectedId} onClick={() => guardDraft(onDuplicate)}>
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
        <p className="mt-8 text-sm text-booki-muted">Select a profile to view its prompts.</p>
      ) : (
        <>
          {error && <p className="mt-4 text-sm text-rose-400">{error}</p>}

          <div className="mt-6 flex flex-wrap items-end justify-between gap-4">
            <div className="min-w-[16rem] flex-1">
              <Field label="AI Profile name">
                <Input value={name} maxLength={120} onChange={(e) => setName(e.target.value)} />
              </Field>
            </div>
            <Button onClick={save} disabled={!isDirty || saving}>
              {saving ? 'Saving…' : isDirty ? 'Save changes' : 'Saved'}
            </Button>
          </div>

          <div className="mt-6 grid gap-6 md:grid-cols-[15rem_1fr]">
            <nav className="space-y-4">
              <div>
                <p className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-booki-muted">
                  Reader profiles
                </p>
                <button
                  onClick={() => setSelectedKey(READER_KEY)}
                  className={`flex w-full items-center justify-between gap-2 rounded-md px-2 py-1.5 text-left text-xs transition ${
                    showReader ? 'bg-booki-accent/15 text-white' : 'text-white/70 hover:bg-white/5 hover:text-white'
                  }`}
                >
                  <span>Edit reader profiles{selectedReader ? ` · ${selectedReader.name}` : ''}</span>
                  {readerDirty && (
                    <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-booki-accent" title="Unsaved" />
                  )}
                </button>
              </div>

              <div>
                <p className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-booki-muted">
                  AI Profile — {profile?.name}
                </p>
              </div>

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
              {showReader ? (
                <div className="space-y-4">
                  <div>
                    <h2 className="text-sm font-bold text-white">Reader profiles</h2>
                    <p className="mt-1 text-[11px] text-white/40">
                      Who is reading, in one study context (languages, sciences, philosophy…). Not tied
                      to any AI Profile — you pick a reader profile when you create a session. Editing
                      one changes it for every session that uses it.
                    </p>
                  </div>

                  <Field label="Editing">
                    <Select value={effectiveReaderId ?? ''} onChange={(e) => onPickReader(e.target.value)}>
                      {readers.profiles.map((r) => (
                        <option key={r.id} value={r.id}>
                          {r.name}
                          {r.isDefault ? ' — default' : ''}
                        </option>
                      ))}
                      <option value={NEW_READER}>＋ New reader profile…</option>
                    </Select>
                  </Field>

                  {selectedReader && (
                    <>
                      {selectedReader.readOnly && (
                        <p className="rounded-lg bg-white/[0.03] px-3 py-2 text-[11px] text-white/50 ring-1 ring-white/10">
                          This is the built-in default reader — read-only. Duplicate it to make your own,
                          editable one.
                        </p>
                      )}

                      <div className="grid gap-3 sm:grid-cols-2">
                        <Field label="Name">
                          <Input
                            value={readerDraft.name}
                            maxLength={120}
                            disabled={selectedReader.readOnly}
                            onChange={(e) => setReaderDraft((d) => ({ ...d, name: e.target.value }))}
                          />
                        </Field>
                        <Field label="Reader level (also goes into the assistant's prompt)">
                          <Select
                            value={readerDraft.readerLevel}
                            disabled={selectedReader.readOnly}
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

                      <TextArea
                        value={readerDraft.context}
                        maxLength={4000}
                        disabled={selectedReader.readOnly}
                        onChange={(e) => setReaderDraft((d) => ({ ...d, context: e.target.value }))}
                        rows={9}
                        className="font-mono text-[13px] leading-relaxed disabled:opacity-60"
                        placeholder={
                          'Describe the reader for this context: their goal, how much they already know, how ' +
                          'they like to learn (examples, definitions, pace), and anything that helps them ' +
                          '(short paragraphs, dyslexia-friendly formatting…).'
                        }
                      />

                      <div className="flex flex-wrap items-center gap-3">
                        {selectedReader.readOnly ? (
                          <Button size="sm" onClick={() => duplicateReader(selectedReader.id)}>
                            Duplicate to edit
                          </Button>
                        ) : (
                          <>
                            <Button size="sm" onClick={onSaveReader} disabled={!readerDirty || readerSaving}>
                              {readerSaving ? 'Saving…' : readerDirty ? 'Save reader profile' : 'Saved'}
                            </Button>
                            <Button variant="secondary" size="sm" onClick={() => duplicateReader(selectedReader.id)}>
                              Duplicate
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={async () => {
                                setActionError(null);
                                try {
                                  await readers.remove(selectedReader.id);
                                  setEditingReaderId(null);
                                } catch (err) {
                                  setActionError(getErrorMessage(err, 'Could not delete that reader profile.'));
                                }
                              }}
                            >
                              Delete
                            </Button>
                          </>
                        )}
                      </div>
                    </>
                  )}
                </div>
              ) : activeSlot ? (
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

                  {activeSlot.group === 'persona' && (
                    <p className="mt-1 text-[11px] text-white/40">
                      The assistant's character: give it a name and gender if you want one, set its tone, and say
                      how it teaches (steps, analogies, how much it pushes back).
                    </p>
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
                          A disabled capability is off everywhere for the session: BooKI never triggers it on
                          its own, and its quick-action button is hidden in the chat. The text below only tunes
                          how eagerly BooKI reaches for the enabled ones.
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
        </>
      )}

      <ConfirmDialog
        open={confirmDelete}
        title="Delete this AI Profile?"
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
        open={!!pendingDiscard}
        title="Discard unsaved changes?"
        description="You have unsaved edits on an AI Profile or a reader profile. Leaving now loses them."
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
