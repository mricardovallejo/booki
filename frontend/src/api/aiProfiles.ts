import api from './client';
import { ENDPOINTS } from '../config/endpoints';
import type { AiProfile, AiProfileSummary } from '../types';

export const listAiProfiles = () =>
  api.get<AiProfileSummary[]>(ENDPOINTS.aiProfiles.list).then((r) => r.data);

export const getAiProfile = (id: number) =>
  api.get<AiProfile>(ENDPOINTS.aiProfiles.byId(id)).then((r) => r.data);

export const duplicateAiProfile = (id: number, name?: string) =>
  api.post<AiProfile>(ENDPOINTS.aiProfiles.duplicate(id), name ? { name } : {}).then((r) => r.data);

export interface UpdateAiProfileRequest {
  name?: string;
  persona?: string;
  isDefault?: boolean;
}

export const updateAiProfile = (id: number, payload: UpdateAiProfileRequest) =>
  api.patch<AiProfile>(ENDPOINTS.aiProfiles.byId(id), payload).then((r) => r.data);

/** Reset just the persona text to its original template. */
export const revertAiProfilePersona = (id: number) =>
  api.post<AiProfile>(ENDPOINTS.aiProfiles.revert(id), {}).then((r) => r.data);

/** Reset the whole profile (just the persona) to its original template. */
export const restoreAiProfile = (id: number) =>
  api.post<AiProfile>(ENDPOINTS.aiProfiles.restore(id), {}).then((r) => r.data);

export const deleteAiProfile = (id: number) => api.delete(ENDPOINTS.aiProfiles.delete(id));
