import { useEffect, useState } from 'react';
import { getAiProfile } from '../api/aiProfiles';
import type { AiProfileSlot } from '../types';

/**
 * Read-only view of an AI Profile's slots, for screens that need to *show* a
 * prompt (e.g. the quiz panel showing what the chosen difficulty means) without
 * the full editing machinery of {@link useAiProfile}.
 */
export function useAiProfileSlots(id: number | null | undefined) {
  const [slots, setSlots] = useState<AiProfileSlot[]>([]);

  useEffect(() => {
    if (!id) {
      setSlots([]);
      return;
    }
    let alive = true;
    getAiProfile(id)
      .then((p) => alive && setSlots(p.slots))
      .catch(() => alive && setSlots([]));
    return () => {
      alive = false;
    };
  }, [id]);

  return slots;
}
