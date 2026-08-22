import type { AiProvider } from '../types';

export const AI_PROVIDER_LABELS: Record<AiProvider, string> = {
  claude: 'Claude',
  openai: 'OpenAI',
  kimi: 'Kimi (best for very long documents)',
  ollama: 'Ollama (local, no cost)'
};
