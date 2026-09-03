import { useCallback, useEffect, useMemo, useState } from 'react';
import { getAiProfile, restoreAiProfile, revertAiProfileSlot, updateAiProfile } from '../api/aiProfiles';
import { getErrorMessage } from '../lib/errors';
import type { AiProfile, CapabilityHint, ReaderLevel } from '../types';

type SlotDraft = Record<string, string>;

/**
 * Loads one AI Profile and holds an in-memory draft of its editable fields (name,
 * reader level, slot bodies) so the editor screen stays purely presentational: it
 * reads `draft`/`name`/`readerLevel` and calls back into the setters / `save` /
 * `revertSlot`.
 *
 * `onMutated` is invoked after any successful save/revert so a caller showing the
 * profile list can refresh it.
 */
export function useAiProfile(id: number, onMutated?: () => void) {
  const [profile, setProfile] = useState<AiProfile | null>(null);
  const [name, setName] = useState('');
  const [readerLevel, setReaderLevel] = useState<ReaderLevel | null>(null);
  const [enabledCapabilities, setEnabledCapabilities] = useState<CapabilityHint[]>([]);
  const [draft, setDraft] = useState<SlotDraft>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const hydrate = useCallback((next: AiProfile) => {
    setProfile(next);
    setName(next.name);
    setReaderLevel(next.readerLevel);
    setEnabledCapabilities(next.enabledCapabilities);
    setDraft(Object.fromEntries(next.slots.map((s) => [s.key, s.content])));
  }, []);

  const toggleCapability = useCallback((cap: CapabilityHint) => {
    setEnabledCapabilities((prev) =>
      prev.includes(cap) ? prev.filter((c) => c !== cap) : [...prev, cap]
    );
  }, []);

  useEffect(() => {
    if (!id) {
      setProfile(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    getAiProfile(id)
      .then((result) => {
        hydrate(result);
        setError(null);
      })
      .catch((err) => setError(getErrorMessage(err, 'Could not load this AI Profile.')))
      .finally(() => setLoading(false));
  }, [id, hydrate]);

  const setSlotDraft = useCallback((key: string, content: string) => {
    setDraft((prev) => ({ ...prev, [key]: content }));
  }, []);

  const dirtySlots = useMemo(
    () => (profile ? profile.slots.filter((s) => (draft[s.key] ?? s.content) !== s.content) : []),
    [profile, draft]
  );
  const nameChanged = !!profile && name.trim().length > 0 && name.trim() !== profile.name;
  const levelChanged = !!profile && readerLevel !== profile.readerLevel;
  const capsChanged =
    !!profile &&
    (enabledCapabilities.length !== profile.enabledCapabilities.length ||
      enabledCapabilities.some((c) => !profile.enabledCapabilities.includes(c)));
  const isDirty = dirtySlots.length > 0 || nameChanged || levelChanged || capsChanged;

  const save = useCallback(async () => {
    if (!profile || !isDirty) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await updateAiProfile(id, {
        name: nameChanged ? name.trim() : undefined,
        readerLevel: levelChanged ? readerLevel : undefined,
        enabledCapabilities: capsChanged ? enabledCapabilities : undefined,
        slots: dirtySlots.map((s) => ({ key: s.key, content: draft[s.key] ?? s.content }))
      });
      hydrate(updated);
      onMutated?.();
    } catch (err) {
      setError(getErrorMessage(err, 'Could not save your changes.'));
    } finally {
      setSaving(false);
    }
  }, [
    profile,
    isDirty,
    id,
    nameChanged,
    name,
    levelChanged,
    readerLevel,
    capsChanged,
    enabledCapabilities,
    dirtySlots,
    draft,
    hydrate,
    onMutated
  ]);

  const revertSlot = useCallback(
    async (key: string) => {
      setError(null);
      try {
        hydrate(await revertAiProfileSlot(id, key));
        onMutated?.();
      } catch (err) {
        setError(getErrorMessage(err, 'Could not restore the original text.'));
      }
    },
    [id, hydrate, onMutated]
  );

  const restore = useCallback(async () => {
    setError(null);
    try {
      hydrate(await restoreAiProfile(id));
      onMutated?.();
    } catch (err) {
      setError(getErrorMessage(err, 'Could not restore this profile.'));
    }
  }, [id, hydrate, onMutated]);

  return {
    profile,
    name,
    setName,
    readerLevel,
    setReaderLevel,
    enabledCapabilities,
    toggleCapability,
    draft,
    setSlotDraft,
    loading,
    saving,
    error,
    isDirty,
    save,
    revertSlot,
    restore
  };
}
