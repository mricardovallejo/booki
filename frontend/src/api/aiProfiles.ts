import api from './client';
import { ENDPOINTS } from '../config/endpoints';
import type { AiProfile, AiProfileSummary, CapabilityHint, ReaderLevel } from '../types';

export const listAiProfiles = () =>
  api.get<AiProfileSummary[]>(ENDPOINTS.aiProfiles.list).then((r) => r.data);

export const getAiProfile = (id: number) =>
  api.get<AiProfile>(ENDPOINTS.aiProfiles.byId(id)).then((r) => r.data);

export const duplicateAiProfile = (id: number, name?: string) =>
  api.post<AiProfile>(ENDPOINTS.aiProfiles.duplicate(id), name ? { name } : {}).then((r) => r.data);

export interface UpdateAiProfileRequest {
  name?: string;
  readerLevel?: ReaderLevel | null;
  enabledCapabilities?: CapabilityHint[];
  slots?: { key: string; content: string }[];
}

export const updateAiProfile = (id: number, payload: UpdateAiProfileRequest) =>
  api.patch<AiProfile>(ENDPOINTS.aiProfiles.byId(id), payload).then((r) => r.data);

export const revertAiProfileSlot = (id: number, key: string) =>
  api.post<AiProfile>(ENDPOINTS.aiProfiles.revert(id), { key }).then((r) => r.data);

/** Reset the whole profile (all slots, reader level, capabilities) to its original template. */
export const restoreAiProfile = (id: number) =>
  api.post<AiProfile>(ENDPOINTS.aiProfiles.restore(id), {}).then((r) => r.data);

export const deleteAiProfile = (id: number) => api.delete(ENDPOINTS.aiProfiles.delete(id));
