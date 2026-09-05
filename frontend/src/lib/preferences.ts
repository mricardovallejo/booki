import type { SessionLanguage } from '../types';

const KEY = 'booki-default-language';

export const LANGUAGE_LABELS: Record<SessionLanguage, string> = {
  en: 'English',
  es: 'Español',
  fr: 'Français'
};

export function isSessionLanguage(value: string): value is SessionLanguage {
  return value in LANGUAGE_LABELS;
}

/** Narrow a raw <select> value to a SessionLanguage, falling back to English. */
export function toSessionLanguage(value: string): SessionLanguage {
  return isSessionLanguage(value) ? value : 'en';
}

export function getDefaultLanguage(): SessionLanguage {
  const stored = localStorage.getItem(KEY);
  if (stored && isSessionLanguage(stored)) return stored;
  return 'en';
}

export function setDefaultLanguage(lang: SessionLanguage) {
  localStorage.setItem(KEY, lang);
}
