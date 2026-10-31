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

export const forgotPassword = (email: string, deliverTo?: string) =>
  api.post<void>(ENDPOINTS.auth.forgotPassword, { email, deliverTo }).then(() => undefined);

export const resetPassword = (token: string, newPassword: string) =>
  api.post<void>(ENDPOINTS.auth.resetPassword, { token, newPassword }).then(() => undefined);
