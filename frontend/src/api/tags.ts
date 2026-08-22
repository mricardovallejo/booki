import api from './client';
import { ENDPOINTS } from '../config/endpoints';
import type { Tag } from '../types';

export const listTags = () => api.get<Tag[]>(ENDPOINTS.tags.list).then((r) => r.data);

export interface CreateTagRequest {
  name: string;
  documentIds?: number[];
}

export const createTag = (payload: CreateTagRequest) =>
  api.post<Tag>(ENDPOINTS.tags.create, payload).then((r) => r.data);

export const renameTag = (id: number, name: string) =>
  api.patch<Tag>(ENDPOINTS.tags.byId(id), { name }).then((r) => r.data);

export const deleteTag = (id: number) => api.delete(ENDPOINTS.tags.byId(id));

export const addDocumentToTag = (tagId: number, documentId: number) =>
  api.put<Tag>(ENDPOINTS.tags.addDocument(tagId, documentId)).then((r) => r.data);

export const removeDocumentFromTag = (tagId: number, documentId: number) =>
  api.delete<Tag>(ENDPOINTS.tags.removeDocument(tagId, documentId)).then((r) => r.data);
