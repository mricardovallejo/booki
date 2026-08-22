import api from './client';
import { ENDPOINTS } from '../config/endpoints';
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

export const getDocumentFileUrl = (id: number) => `/api${ENDPOINTS.documents.file(id)}`;
