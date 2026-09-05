import api from './client';
import { ENDPOINTS } from '../config/endpoints';
import type { ReaderLevel, ReaderProfile } from '../types';

export const listReaderProfiles = () =>
  api.get<ReaderProfile[]>(ENDPOINTS.readerProfiles.list).then((r) => r.data);

export interface CreateReaderProfileRequest {
  name: string;
  context?: string;
  readerLevel?: ReaderLevel | null;
  /** Copy from this reader profile (defaults to the built-in one). */
  fromId?: number;
}

export const createReaderProfile = (payload: CreateReaderProfileRequest) =>
  api.post<ReaderProfile>(ENDPOINTS.readerProfiles.create, payload).then((r) => r.data);

export interface UpdateReaderProfileRequest {
  name?: string;
  context?: string;
  /** A ReaderLevel, or "" to clear it. Omit to leave unchanged. */
  readerLevel?: ReaderLevel | '';
  isDefault?: true;
}

export const updateReaderProfile = (id: number, payload: UpdateReaderProfileRequest) =>
  api.patch<ReaderProfile>(ENDPOINTS.readerProfiles.byId(id), payload).then((r) => r.data);

export const deleteReaderProfile = (id: number) => api.delete(ENDPOINTS.readerProfiles.delete(id));
