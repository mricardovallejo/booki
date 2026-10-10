import api from './client';
import { ENDPOINTS } from '../config/endpoints';
import type { CapabilityHint, User } from '../types';

export const getMe = () => api.get<User>(ENDPOINTS.users.me).then((r) => r.data);

export interface UpdateUserRequest {
  name?: string;
  enabledCapabilities?: CapabilityHint[];
}

export const updateMe = (payload: UpdateUserRequest) =>
  api.patch<User>(ENDPOINTS.users.updateMe, payload).then((r) => r.data);
