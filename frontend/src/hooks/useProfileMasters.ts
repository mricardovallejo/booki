import { useEffect, useState } from 'react';
import { listProfileMasters } from '../api/profileMasters';
import { getErrorMessage } from '../lib/errors';
import type { ProfileMaster } from '../types';

export function useProfileMasters() {
  const [masters, setMasters] = useState<ProfileMaster[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listProfileMasters()
      .then((result) => {
        setMasters(result);
        setError(null);
      })
      .catch((err) => setError(getErrorMessage(err, 'Could not load Profile Masters.')));
  }, []);

  return { masters, error };
}
