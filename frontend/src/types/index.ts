export type SessionLanguage = 'en' | 'es' | 'fr';
/** Conversational capability a quick-action button asks the backend to run directly. */
export type CapabilityHint = 'quiz' | 'summary' | 'explain' | 'mnemonic';
export type Difficulty = 'easy' | 'medium' | 'hard';
export type AiProvider = 'claude' | 'openai' | 'kimi' | 'ollama';

export interface User {
  id: number;
  email: string;
  name: string;
  bio?: string;
  systemPrompt?: string;
  createdAt: string;
}

export interface SessionContext {
  appPrompt: string;
  masterPrompt: string | null;
  userPrompt: string | null;
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
  profileMasterId?: number | null;
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

export interface ProfileMaster {
  id: number;
  name: string;
  description: string;
  systemPrompt?: string;
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
  profileMasterId: number | null;
  masterName: string | null;
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
  masterName: string | null;
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
