/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend API root for a deployed build (separate origin). Unset in local dev. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
