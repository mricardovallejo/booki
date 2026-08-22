import type { SessionLanguage } from '../types';

const KEY = 'booki-default-language';

export const LANGUAGE_LABELS: Record<SessionLanguage, string> = {
  en: 'English',
  es: 'Español',
  fr: 'Français'
};

export function getDefaultLanguage(): SessionLanguage {
  const stored = localStorage.getItem(KEY);
  if (stored === 'en' || stored === 'es' || stored === 'fr') return stored;
  return 'en';
}

export function setDefaultLanguage(lang: SessionLanguage) {
  localStorage.setItem(KEY, lang);
}
