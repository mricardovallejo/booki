export type SessionLanguage = 'en' | 'es' | 'fr';
/** Conversational capability a quick-action button asks the backend to run directly. */
export type CapabilityHint = 'quiz' | 'summary' | 'explain' | 'mnemonic';
export type Difficulty = 'easy' | 'medium' | 'hard';
export type AiProvider = 'claude' | 'openai' | 'kimi' | 'ollama';

export interface User {
  id: number;
  email: string;
  name: string;
  createdAt: string;
}

/** Which family a prompt slot belongs to, used to group them in the editor. */
export type AiProfileSlotGroup = 'persona' | 'reader' | 'difficulty' | 'functions' | 'routing';

/**
 * One SlotPrompt inside an AI Profile. `text` is the editable body; the locked
 * frame (`lockedPreamble` / `lockedPostamble`) is shown for context but cannot
 * be changed because the app depends on its shape. `originalText` is the shipped
 * baseline used for the Edited/Original badge and "restore original text".
 */
export interface AiProfileSlot {
  key: string;
  label: string;
  group: AiProfileSlotGroup;
  lockedPreamble: string | null;
  lockedPostamble: string | null;
  text: string;
  originalText: string;
  modified: boolean;
}

/** Structured self-assessed level, used to suggest a session difficulty. */
export type ReaderLevel = 'beginner' | 'intermediate' | 'advanced';

export interface AiProfileSummary {
  id: number;
  name: string;
  /** The shipped template this profile is a copy of — the target of "restore to original". */
  basedOnId: number | null;
  isDefault: boolean;
  readerLevel: ReaderLevel | null;
  /** Which conversational capabilities this profile allows (subset of CapabilityHint). */
  enabledCapabilities: CapabilityHint[];
  updatedAt: string;
  slotCount: number;
  modifiedCount: number;
}

export interface AiProfile extends AiProfileSummary {
  slots: AiProfileSlot[];
}

export type SessionContextGroup =
  | 'core'
  | 'difficulty'
  | 'persona'
  | 'reader'
  | 'functions'
  | 'routing'
  | 'session';

/** One part of the instructions BooKI reads before answering, as shown in the context panel. */
export interface SessionContextLayer {
  key: string;
  group: SessionContextGroup;
  label: string;
  editable: boolean;
  source: string;
  content: string | null;
}

export interface SessionContext {
  aiProfileId: number | null;
  aiProfileName: string | null;
  language: SessionLanguage;
  difficulty: Difficulty;
  enabledCapabilities: CapabilityHint[];
  layers: SessionContextLayer[];
}

export interface Document {
  id: number;
  title: string;
  pageCount: number;
  createdAt: string;
}

export interface Session {
  id: number;
  documentId: number;
  title: string;
  startPage: number;
  endPage: number;
  currentPage: number;
  difficulty: Difficulty;
  aiProfileId?: number | null;
  enabledCapabilities: CapabilityHint[];
  language: SessionLanguage;
  aiProvider: AiProvider;
  createdAt: string;
}

export interface Message {
  id: number;
  speaker: 'USER' | 'BOOKI';
  inputType: 'TEXT' | 'VOICE';
  message: string;
  createdAt: string;
}

export interface Tag {
  id: number;
  name: string;
  documentIds: number[];
}

export interface QuizQuestion {
  id: number;
  pageNumber: number;
  question: string;
}

export interface QuizConfig {
  aiProfileId: number | null;
  profileName: string | null;
  difficulty: Difficulty;
  questionCount: number;
}

export interface QuizGenerateResult {
  questions: QuizQuestion[];
  config: QuizConfig;
}

export interface QuizAnswerResult {
  correct: boolean;
  score: number;
  feedback: string;
}

export interface QuizAttempt {
  id: number;
  pageNumber: number;
  question: string;
  answer: string;
  correct: boolean;
  score: number;
  feedback: string;
  difficulty: Difficulty;
  profileName: string | null;
  createdAt: string;
}

export interface QuizReport {
  attempts: QuizAttempt[];
  summary: {
    total: number;
    correct: number;
    incorrect: number;
    averageScore: number;
  };
}

export interface SessionProgress {
  pagesRead: number;
  totalPages: number;
  pctRead: number;
  messageCount: number;
  quizzesTaken: number;
  quizAverageScore: number;
}

export interface SessionNotification {
  id: number;
  type: 'progress' | 'chat' | 'quiz';
  message: string;
  createdAt: string;
}

export interface SentReport {
  id: number;
  sessionId: number;
  type: 'progress' | 'quiz' | 'summary';
  email: string | null;
  downloadUrl: string;
  simulated: boolean;
  createdAt: string;
}

export type SummaryDeliverAs = 'chat' | 'pdf';

export interface GenerateSummaryRequest {
  lengthPages: number;
  prompt?: string;
  includeCover: boolean;
  deliverAs: SummaryDeliverAs;
  email?: string;
}
