import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useAiProfiles } from '../hooks/useAiProfiles';
import { useAiProfile } from '../hooks/useAiProfile';
import { getAiProfile } from '../api/aiProfiles';
import { ROUTES } from '../config/routes';
import { getErrorMessage } from '../lib/errors';
import Button from '../components/ui/Button';
import ConfirmDialog from '../components/ConfirmDialog';
import { Field, Input, Select, TextArea } from '../components/ui/FormField';
import type { AiProfileSlot, AiProfileSlotGroup, CapabilityHint } from '../types';

const CAPABILITY_LABELS: { hint: CapabilityHint; label: string }[] = [
  { hint: 'quiz', label: 'Quiz' },
  { hint: 'summary', label: 'Summary' },
  { hint: 'explain', label: 'Explain' },
  { hint: 'mnemonic', label: 'Mnemonic' }
];

const BASIC_GROUPS: AiProfileSlotGroup[] = ['persona', 'reader', 'difficulty'];
const ADVANCED_GROUPS: AiProfileSlotGroup[] = ['functions', 'routing'];
const GROUP_LABEL: Record<AiProfileSlotGroup, string> = {
  persona: 'Persona',
  reader: 'Reader context',
  difficulty: 'Difficulty levels',
  functions: 'Functions',
  routing: 'Capability routing'
};

const LABEL_PREFIX_RE = /^Function — |^Difficulty — /;

export default function AiProfilesPage() {
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const slotParam = searchParams.get('slot');
  const navigate = useNavigate();
  const { profiles, loading: listLoading, error: listError, refresh, duplicate, remove } = useAiProfiles();

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
    readerLevel,
    setReaderLevel,
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

  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [showAdvanced, setShowAdvanced] = useState(false);
  useEffect(() => setSelectedKey(null), [selectedId]);

  // Deep link like /ai-profiles/5?slot=rubric_hard preselects that slot and
  // opens the Advanced section if the slot lives there.
  useEffect(() => {
    const slot = profile?.slots.find((s) => s.key === slotParam);
    if (slot) {
      setSelectedKey(slot.key);
      if (ADVANCED_GROUPS.includes(slot.group)) setShowAdvanced(true);
    }
  }, [slotParam, profile]);

  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [confirmRestore, setConfirmRestore] = useState(false);
  const [copyingContext, setCopyingContext] = useState(false);

  // Reader context lives per profile, so pull it in from another one instead of
  // retyping (matters most for accessibility notes).
  const copyReaderContextFrom = async (fromId: number) => {
    setCopyingContext(true);
    setActionError(null);
    try {
      const other = await getAiProfile(fromId);
      setSlotDraft('reader_context', other.slots.find((s) => s.key === 'reader_context')?.text ?? '');
    } catch (err) {
      setActionError(getErrorMessage(err, 'Could not copy from that profile.'));
    } finally {
      setCopyingContext(false);
    }
  };

  // Guard unsaved edits: warn on tab close, and confirm before switching profile
  // or duplicating.
  const [pendingDiscard, setPendingDiscard] = useState<(() => void) | null>(null);
  const guardDraft = (run: () => void) => {
    if (isDirty) setPendingDiscard(() => run);
    else run();
  };
  useEffect(() => {
    if (!isDirty) return;
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [isDirty]);

  const groupsFor = (which: AiProfileSlotGroup[]) => {
    const slots = profile?.slots ?? [];
    return which
      .map((group) => ({ group, slots: slots.filter((s) => s.group === group) }))
      .filter((g) => g.slots.length > 0);
  };
  const basicGrouped = useMemo(() => groupsFor(BASIC_GROUPS), [profile]);
  const advancedGrouped = useMemo(() => groupsFor(ADVANCED_GROUPS), [profile]);
  const advancedEditedCount = useMemo(
    () =>
      (profile?.slots ?? []).filter(
        (s) => ADVANCED_GROUPS.includes(s.group) && (draft[s.key] ?? s.text) !== s.originalText
      ).length,
    [profile, draft]
  );

  const activeKey = selectedKey ?? profile?.slots[0]?.key ?? null;
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

  const dirtyOrModified = isDirty || (profile?.slots.some((s) => s.modified) ?? false);

  return (
    <div className="mx-auto min-h-screen max-w-5xl px-6 py-10">
      <h1 className="text-2xl font-bold text-white">AI Profiles</h1>
      <p className="mt-1 max-w-2xl text-sm text-booki-muted">
        An AI Profile is the full set of prompts a reading session runs on — persona, difficulty
        levels, and how each function behaves. Each is yours to edit; "Restore to original" brings a
        profile back to how it shipped. The BooKI core stays fixed and is never part of a profile.
      </p>

      {listError && <p className="mt-4 text-sm text-rose-400">{listError}</p>}
      {actionError && <p className="mt-4 text-sm text-rose-400">{actionError}</p>}

      <div className="mt-6 flex flex-wrap items-end gap-3">
        <div className="min-w-[16rem] flex-1">
          <Field label="Profile">
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
              <Field label="Profile name">
                <Input value={name} onChange={(e) => setName(e.target.value)} />
              </Field>
            </div>
            <Button onClick={save} disabled={!isDirty || saving}>
              {saving ? 'Saving…' : isDirty ? 'Save changes' : 'Saved'}
            </Button>
          </div>

          <div className="mt-6 grid gap-6 md:grid-cols-[15rem_1fr]">
            <nav className="space-y-4">
              {basicGrouped.map(({ group, slots }) => (
                <SlotGroup
                  key={group}
                  label={GROUP_LABEL[group]}
                  slots={slots}
                  draft={draft}
                  activeKey={activeKey}
                  onSelect={setSelectedKey}
                />
              ))}

              <div className="border-t border-white/10 pt-3">
                <button
                  onClick={() => setShowAdvanced((s) => !s)}
                  className="flex w-full items-center justify-between text-[11px] font-semibold uppercase tracking-wide text-booki-muted hover:text-white"
                >
                  <span>{showAdvanced ? '▾' : '▸'} Advanced</span>
                  {!showAdvanced && advancedEditedCount > 0 && (
                    <span className="h-1.5 w-1.5 rounded-full bg-booki-accent" title="Has edits" />
                  )}
                </button>
                {showAdvanced && (
                  <div className="mt-2 space-y-4">
                    {advancedGrouped.map(({ group, slots }) => (
                      <SlotGroup
                        key={group}
                        label={GROUP_LABEL[group]}
                        slots={slots}
                        draft={draft}
                        activeKey={activeKey}
                        onSelect={setSelectedKey}
                      />
                    ))}
                  </div>
                )}
              </div>
            </nav>

            {activeSlot && (
              <section className="min-w-0">
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

                {activeSlot.group === 'reader' && (
                  <div className="mt-3 space-y-3">
                    <Field label="Reader level (suggests a session difficulty)">
                      <Select
                        value={readerLevel ?? ''}
                        onChange={(e) => setReaderLevel((e.target.value || null) as typeof readerLevel)}
                      >
                        <option value="">Not set</option>
                        <option value="beginner">Beginner</option>
                        <option value="intermediate">Intermediate</option>
                        <option value="advanced">Advanced</option>
                      </Select>
                    </Field>
                    {profiles.filter((p) => p.id !== selectedId).length > 0 && (
                      <Field label="Copy reader context from another profile">
                        <Select
                          value=""
                          disabled={copyingContext}
                          onChange={(e) => e.target.value && copyReaderContextFrom(Number(e.target.value))}
                        >
                          <option value="">{copyingContext ? 'Copying…' : 'Choose a profile…'}</option>
                          {profiles
                            .filter((p) => p.id !== selectedId)
                            .map((p) => (
                              <option key={p.id} value={p.id}>
                                {p.name}
                              </option>
                            ))}
                        </Select>
                      </Field>
                    )}
                  </div>
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
                        A disabled capability is off everywhere for the session: BooKI never triggers
                        it on its own, and its quick-action button is hidden in the chat. The text
                        below only tunes how eagerly BooKI reaches for the enabled ones.
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
                  rows={8}
                  className="mt-2 font-mono text-[13px] leading-relaxed"
                  placeholder={
                    activeSlot.group === 'reader'
                      ? 'Describe the reader for this profile: their goal, how much they already know, how they like to learn.'
                      : 'Prompt text…'
                  }
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
              </section>
            )}
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
            ? `Every prompt, the reader level and the capabilities of "${profile.name}" go back to how it shipped. Its name stays.`
            : undefined
        }
        confirmLabel="Restore"
        onConfirm={onRestore}
        onCancel={() => setConfirmRestore(false)}
      />

      <ConfirmDialog
        open={!!pendingDiscard}
        title="Discard unsaved changes?"
        description="You edited this profile but haven't saved. Leaving now loses those edits."
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
