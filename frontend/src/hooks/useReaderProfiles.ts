import { useCallback, useEffect, useState } from 'react';
import {
  createReaderProfile,
  deleteReaderProfile,
  listReaderProfiles,
  updateReaderProfile,
  type CreateReaderProfileRequest,
  type UpdateReaderProfileRequest
} from '../api/readerProfiles';
import { getErrorMessage } from '../lib/errors';
import type { ReaderProfile } from '../types';

/** The current user's reader profiles (who they are, per study context). */
export function useReaderProfiles() {
  const [profiles, setProfiles] = useState<ReaderProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    setLoading(true);
    return listReaderProfiles()
      .then((result) => {
        setProfiles(result);
        setError(null);
      })
      .catch((err) => setError(getErrorMessage(err, 'Could not load reader profiles.')))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const create = useCallback(
    async (payload: CreateReaderProfileRequest) => {
      const created = await createReaderProfile(payload);
      await refresh();
      return created;
    },
    [refresh]
  );

  const update = useCallback(
    async (id: number, payload: UpdateReaderProfileRequest) => {
      const updated = await updateReaderProfile(id, payload);
      await refresh();
      return updated;
    },
    [refresh]
  );

  const remove = useCallback(
    async (id: number) => {
      await deleteReaderProfile(id);
      await refresh();
    },
    [refresh]
  );

  return { profiles, loading, error, refresh, create, update, remove };
}
