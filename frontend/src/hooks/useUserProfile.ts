import { useCallback, useState } from 'react';
import { updateMe, type UpdateUserRequest } from '../api/users';
import { useAuth } from '../context/AuthContext';

export function useUserProfile() {
  const { user, updateUser } = useAuth();
  const [saving, setSaving] = useState(false);

  const save = useCallback(
    async (payload: UpdateUserRequest) => {
      setSaving(true);
      try {
        const updated = await updateMe(payload);
        updateUser(updated);
        return updated;
      } finally {
        setSaving(false);
      }
    },
    [updateUser]
  );

  return { user, saving, save };
}
