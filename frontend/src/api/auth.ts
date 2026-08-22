import api from './client';
import { ENDPOINTS } from '../config/endpoints';
import type { User } from '../types';

export interface AuthResponse {
  token: string;
  user: User;
}

export interface AuthRequest {
  email: string;
  password: string;
  name?: string;
}

export const login = (payload: AuthRequest) =>
  api.post<AuthResponse>(ENDPOINTS.auth.login, payload).then((r) => r.data);

export const register = (payload: AuthRequest) =>
  api.post<AuthResponse>(ENDPOINTS.auth.register, payload).then((r) => r.data);
