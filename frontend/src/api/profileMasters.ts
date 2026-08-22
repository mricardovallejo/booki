import api from './client';
import { ENDPOINTS } from '../config/endpoints';
import type { ProfileMaster } from '../types';

export const listProfileMasters = () =>
  api.get<ProfileMaster[]>(ENDPOINTS.profileMasters.list).then((r) => r.data);

export interface CreateProfileMasterRequest {
  name: string;
  description: string;
  systemPrompt: string;
}

export const createProfileMaster = (payload: CreateProfileMasterRequest) =>
  api.post<ProfileMaster>(ENDPOINTS.profileMasters.create, payload).then((r) => r.data);

export interface UpdateProfileMasterRequest {
  name?: string;
  description?: string;
  systemPrompt?: string;
}

export const updateProfileMaster = (id: number, payload: UpdateProfileMasterRequest) =>
  api.patch<ProfileMaster>(ENDPOINTS.profileMasters.update(id), payload).then((r) => r.data);

export const deleteProfileMaster = (id: number) => api.delete(ENDPOINTS.profileMasters.delete(id));
