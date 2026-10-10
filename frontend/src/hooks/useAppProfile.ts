import { useCallback, useEffect, useState } from 'react';
import { listAppProfile, restoreAppProfile, revertAppProfileSlot, updateAppProfile } from '../api/appProfile';
import { getErrorMessage } from '../lib/errors';
import type { AppProfileSlot } from '../types';

type Draft = Record<string, string>;

/**
 * The account-wide App Profile: what each difficulty level means, how BooKI's
 * functions behave, and when it routes to one on its own in chat. Not tied to
 * any tutor profile — one set per account, loaded once here.
 */
export function useAppProfile() {
  const [slots, setSlots] = useState<AppProfileSlot[]>([]);
  const [draft, setDraft] = useState<Draft>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const hydrate = useCallback((next: AppProfileSlot[]) => {
    setSlots(next);
    setDraft(Object.fromEntries(next.map((s) => [s.key, s.text])));
  }, []);

  useEffect(() => {
    listAppProfile()
      .then((result) => {
        hydrate(result);
        setError(null);
      })
      .catch((err) => setError(getErrorMessage(err, 'Could not load the App Profile.')))
      .finally(() => setLoading(false));
  }, [hydrate]);

  const setSlotDraft = useCallback((key: string, text: string) => {
    setDraft((prev) => ({ ...prev, [key]: text }));
  }, []);

  const dirtySlots = slots.filter((s) => (draft[s.key] ?? s.text) !== s.text);
  const isDirty = dirtySlots.length > 0;

  const save = useCallback(async () => {
    if (!isDirty) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await updateAppProfile(dirtySlots.map((s) => ({ key: s.key, text: draft[s.key] ?? s.text })));
      hydrate(updated);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not save the App Profile.'));
    } finally {
      setSaving(false);
    }
  }, [isDirty, dirtySlots, draft, hydrate]);

  const revertSlot = useCallback(
    async (key: string) => {
      setError(null);
      try {
        hydrate(await revertAppProfileSlot(key));
      } catch (err) {
        setError(getErrorMessage(err, 'Could not restore this one.'));
      }
    },
    [hydrate]
  );

  const restore = useCallback(async () => {
    setError(null);
    try {
      hydrate(await restoreAppProfile());
    } catch (err) {
      setError(getErrorMessage(err, 'Could not restore the App Profile.'));
    }
  }, [hydrate]);

  return { slots, draft, setSlotDraft, loading, saving, error, isDirty, save, revertSlot, restore };
}
