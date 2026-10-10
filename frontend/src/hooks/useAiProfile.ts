import { useCallback, useEffect, useState } from 'react';
import { getAiProfile, restoreAiProfile, revertAiProfilePersona, updateAiProfile } from '../api/aiProfiles';
import { getErrorMessage } from '../lib/errors';
import type { AiProfile } from '../types';

/**
 * Loads one AI Profile (name + persona) and holds an in-memory draft of its
 * editable fields so the editor screen stays purely presentational: it reads
 * `personaDraft`/`name` and calls back into the setters / `save` /
 * `revertPersona`.
 *
 * `onMutated` is invoked after any successful save/revert so a caller showing the
 * profile list can refresh it.
 */
export function useAiProfile(id: number, onMutated?: () => void) {
  const [profile, setProfile] = useState<AiProfile | null>(null);
  const [name, setName] = useState('');
  const [personaDraft, setPersonaDraft] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const hydrate = useCallback((next: AiProfile) => {
    setProfile(next);
    setName(next.name);
    setPersonaDraft(next.persona);
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
      .catch((err) => setError(getErrorMessage(err, 'Could not load this tutor profile.')))
      .finally(() => setLoading(false));
  }, [id, hydrate]);

  const personaChanged = !!profile && personaDraft !== profile.persona;
  const nameChanged = !!profile && name.trim().length > 0 && name.trim() !== profile.name;
  const isDirty = personaChanged || nameChanged;

  const save = useCallback(async () => {
    if (!profile || !isDirty) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await updateAiProfile(id, {
        name: nameChanged ? name.trim() : undefined,
        persona: personaChanged ? personaDraft : undefined
      });
      hydrate(updated);
      onMutated?.();
    } catch (err) {
      setError(getErrorMessage(err, 'Could not save your changes.'));
    } finally {
      setSaving(false);
    }
  }, [profile, isDirty, id, nameChanged, name, personaChanged, personaDraft, hydrate, onMutated]);

  const revertPersona = useCallback(async () => {
    setError(null);
    try {
      hydrate(await revertAiProfilePersona(id));
      onMutated?.();
    } catch (err) {
      setError(getErrorMessage(err, 'Could not restore the original text.'));
    }
  }, [id, hydrate, onMutated]);

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
    personaDraft,
    setPersonaDraft,
    loading,
    saving,
    error,
    isDirty,
    save,
    revertPersona,
    restore
  };
}
