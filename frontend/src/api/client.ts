import axios, { type InternalAxiosRequestConfig } from 'axios';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json'
  }
});

api.interceptors.request.use((config) => {
  const raw = localStorage.getItem('booki-auth');
  if (raw) {
    try {
      const { token } = JSON.parse(raw);
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    } catch {
      // ignore
    }
  }
  return config;
});

// Dev-only: one console line per call (method, path, status, timing) so a
// silent failure like "reply came back as text-only" is visible without
// opening the Network tab. Never the auth token, request body or response
// body — those can carry chat text. Stripped from production builds.
if (import.meta.env.DEV) {
  const REQUEST_STARTED_AT = Symbol('requestStartedAt');
  api.interceptors.request.use((config: InternalAxiosRequestConfig & { [REQUEST_STARTED_AT]?: number }) => {
    config[REQUEST_STARTED_AT] = performance.now();
    return config;
  });
  api.interceptors.response.use(
    (response) => {
      const startedAt = (response.config as InternalAxiosRequestConfig & { [REQUEST_STARTED_AT]?: number })[
        REQUEST_STARTED_AT
      ];
      const durationMs = startedAt ? Math.round(performance.now() - startedAt) : undefined;
      console.debug(`[api] ${response.config.method?.toUpperCase()} ${response.config.url} -> ${response.status} (${durationMs}ms)`);
      return response;
    },
    (error) => {
      const config = error.config as (InternalAxiosRequestConfig & { [REQUEST_STARTED_AT]?: number }) | undefined;
      const startedAt = config?.[REQUEST_STARTED_AT];
      const durationMs = startedAt ? Math.round(performance.now() - startedAt) : undefined;
      console.debug(
        `[api] ${config?.method?.toUpperCase()} ${config?.url} -> ${error.response?.status ?? 'network error'} (${durationMs}ms)`
      );
      return Promise.reject(error);
    }
  );
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('booki-auth');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
