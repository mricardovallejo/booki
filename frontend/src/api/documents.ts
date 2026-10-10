import api from './client';
import { API_BASE, ENDPOINTS } from '../config/endpoints';
import type { Document } from '../types';

export const listDocuments = () => api.get<Document[]>(ENDPOINTS.documents.list).then((r) => r.data);

export const uploadDocument = (file: File) => {
  const form = new FormData();
  form.append('file', file);
  return api.post<Document>(ENDPOINTS.documents.create, form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

export const getDocument = (id: number) => api.get<Document>(ENDPOINTS.documents.byId(id)).then((r) => r.data);

export const deleteDocument = (id: number) => api.delete(ENDPOINTS.documents.delete(id));

export const detectPagination = (id: number) =>
  api.post<Document>(ENDPOINTS.documents.detectPagination(id)).then((r) => r.data);

/** `offset: null` clears it back to unknown. */
export const updatePaginationOffset = (id: number, offset: number | null) =>
  api.patch<Document>(ENDPOINTS.documents.updatePagination(id), { offset }).then((r) => r.data);

// A plain URL (react-pdf fetches it directly with an Authorization header),
// so it needs the full base — not just the axios-relative path.
export const getDocumentFileUrl = (id: number) => `${API_BASE}${ENDPOINTS.documents.file(id)}`;
