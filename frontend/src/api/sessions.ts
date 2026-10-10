import api from './client';
import { ENDPOINTS } from '../config/endpoints';
import type {
  CapabilityHint,
  Difficulty,
  GenerateSummaryRequest,
  Message,
  PageMode,
  QuizAnswerResult,
  QuizGenerateResult,
  QuizReport,
  SentReport,
  Session,
  SessionContext,
  SessionLanguage,
  SessionNotification,
  SessionProgress
} from '../types';

export interface CreateSessionRequest {
  documentId: number;
  title: string;
  /** Just where the reader opens — optional, defaults to page 1. Not a locked range. */
  startPage?: number;
  aiProfileId?: number;
  readerProfileId?: number;
  difficulty: Difficulty;
  language: SessionLanguage;
}

export const createSession = (payload: CreateSessionRequest) =>
  api.post<Session>(ENDPOINTS.sessions.create, payload).then((r) => r.data);

export const getSession = (id: number) => api.get<Session>(ENDPOINTS.sessions.byId(id)).then((r) => r.data);

export const updateCurrentPage = (id: number, currentPage: number) =>
  api.patch<Session>(ENDPOINTS.sessions.currentPage(id), { currentPage }).then((r) => r.data);

export const listMessages = (id: number) =>
  api.get<Message[]>(ENDPOINTS.sessions.messages(id)).then((r) => r.data);

export const sendMessage = (
  id: number,
  message: string,
  inputType: 'TEXT' | 'VOICE' = 'TEXT',
  capabilityHint: CapabilityHint | undefined,
  pageRange: { start: number; end: number; mode?: PageMode }
) =>
  api
    .post<Message>(ENDPOINTS.sessions.messages(id), {
      message,
      inputType,
      capabilityHint,
      pageStart: pageRange.start,
      pageEnd: pageRange.end,
      pageMode: pageRange.mode
    })
    .then((r) => r.data);

export interface GenerateQuizRequest {
  difficulty: Difficulty;
  questionCount: number;
  startPage: number;
  endPage: number;
  pageMode?: PageMode;
}

export const generateQuiz = (id: number, payload: GenerateQuizRequest) =>
  api.post<QuizGenerateResult>(ENDPOINTS.sessions.quiz(id), payload).then((r) => r.data);

export interface SubmitQuizAnswerRequest {
  pageNumber: number;
  question: string;
  answer: string;
  difficulty: Difficulty;
  quizRequestId?: number;
}

export const submitQuizAnswer = (id: number, payload: SubmitQuizAnswerRequest) =>
  api.post<QuizAnswerResult>(ENDPOINTS.sessions.quizAnswer(id), payload).then((r) => r.data);

export const getQuizReport = (id: number) =>
  api.get<QuizReport>(ENDPOINTS.sessions.quizAttempts(id)).then((r) => r.data);

export const getProgress = (id: number) =>
  api.get<SessionProgress>(ENDPOINTS.sessions.progress(id)).then((r) => r.data);

export const getNotifications = (id: number) =>
  api.get<SessionNotification[]>(ENDPOINTS.sessions.notifications(id)).then((r) => r.data);

export const getSessionContext = (id: number) =>
  api.get<SessionContext>(ENDPOINTS.sessions.context(id)).then((r) => r.data);

export const listSessionReports = (id: number) =>
  api.get<SentReport[]>(ENDPOINTS.sessions.reports(id)).then((r) => r.data);

export const sendProgressReport = (id: number, email: string) =>
  api.post<SentReport>(ENDPOINTS.sessions.reportProgress(id), { email }).then((r) => r.data);

export const sendQuizReport = (id: number, email: string, quizRequestId?: number) =>
  api.post<SentReport>(ENDPOINTS.sessions.reportQuiz(id), { email, quizRequestId }).then((r) => r.data);

export const generateSummaryAsChat = (id: number, payload: Omit<GenerateSummaryRequest, 'deliverAs'>) =>
  api
    .post<Message>(ENDPOINTS.sessions.summary(id), { ...payload, deliverAs: 'chat' })
    .then((r) => r.data);

export const generateSummaryAsPdf = (id: number, payload: Omit<GenerateSummaryRequest, 'deliverAs'>) =>
  api
    .post<SentReport>(ENDPOINTS.sessions.summary(id), { ...payload, deliverAs: 'pdf' })
    .then((r) => r.data);
