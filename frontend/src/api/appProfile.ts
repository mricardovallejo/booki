import api from './client';
import { ENDPOINTS } from '../config/endpoints';
import type { AppProfileSlot } from '../types';

export const listAppProfile = () => api.get<AppProfileSlot[]>(ENDPOINTS.appProfile.list).then((r) => r.data);

export const updateAppProfile = (slots: { key: string; text: string }[]) =>
  api.put<AppProfileSlot[]>(ENDPOINTS.appProfile.update, { slots }).then((r) => r.data);

export const revertAppProfileSlot = (key: string) =>
  api.post<AppProfileSlot[]>(ENDPOINTS.appProfile.revert(key), {}).then((r) => r.data);

export const restoreAppProfile = () => api.post<AppProfileSlot[]>(ENDPOINTS.appProfile.restore, {}).then((r) => r.data);
