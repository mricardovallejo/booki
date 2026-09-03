import { useCallback, useEffect, useState } from 'react';
import { deleteAiProfile, duplicateAiProfile, listAiProfiles } from '../api/aiProfiles';
import { getErrorMessage } from '../lib/errors';
import type { AiProfile, AiProfileSummary } from '../types';

/** List of the current user's AI Profiles (factory templates + their own copies). */
export function useAiProfiles() {
  const [profiles, setProfiles] = useState<AiProfileSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    setLoading(true);
    return listAiProfiles()
      .then((result) => {
        setProfiles(result);
        setError(null);
      })
      .catch((err) => setError(getErrorMessage(err, 'Could not load AI Profiles.')))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const duplicate = useCallback(
    async (id: number, name?: string): Promise<AiProfile> => {
      const created = await duplicateAiProfile(id, name);
      await refresh();
      return created;
    },
    [refresh]
  );

  const remove = useCallback(
    async (id: number) => {
      await deleteAiProfile(id);
      await refresh();
    },
    [refresh]
  );

  return { profiles, loading, error, refresh, duplicate, remove };
}
