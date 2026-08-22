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
