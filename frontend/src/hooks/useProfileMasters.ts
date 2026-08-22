import { useEffect, useState } from 'react';
import { listProfileMasters } from '../api/profileMasters';
import type { ProfileMaster } from '../types';

export function useProfileMasters() {
  const [masters, setMasters] = useState<ProfileMaster[]>([]);

  useEffect(() => {
    listProfileMasters().then(setMasters).catch(() => setMasters([]));
  }, []);

  return masters;
}
