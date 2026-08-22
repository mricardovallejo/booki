import api from './client';
import { ENDPOINTS } from '../config/endpoints';
import type {
  Difficulty,
  GenerateSummaryRequest,
  Message,
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
  startPage: number;
  endPage: number;
  profileMasterId?: number;
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

export const sendMessage = (id: number, message: string, inputType: 'TEXT' | 'VOICE' = 'TEXT') =>
  api.post<Message>(ENDPOINTS.sessions.messages(id), { message, inputType }).then((r) => r.data);

export interface GenerateQuizRequest {
  profileMasterId?: number | null;
  difficulty: Difficulty;
  questionCount: number;
}

export const generateQuiz = (id: number, payload: GenerateQuizRequest) =>
  api.post<QuizGenerateResult>(ENDPOINTS.sessions.quiz(id), payload).then((r) => r.data);

export interface SubmitQuizAnswerRequest {
  pageNumber: number;
  question: string;
  answer: string;
  difficulty: Difficulty;
  profileMasterId?: number | null;
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

export const sendQuizReport = (id: number, email: string) =>
  api.post<SentReport>(ENDPOINTS.sessions.reportQuiz(id), { email }).then((r) => r.data);

export const generateSummaryAsChat = (id: number, payload: Omit<GenerateSummaryRequest, 'deliverAs'>) =>
  api
    .post<Message>(ENDPOINTS.sessions.summary(id), { ...payload, deliverAs: 'chat' })
    .then((r) => r.data);

export const generateSummaryAsPdf = (id: number, payload: Omit<GenerateSummaryRequest, 'deliverAs'>) =>
  api
    .post<SentReport>(ENDPOINTS.sessions.summary(id), { ...payload, deliverAs: 'pdf' })
    .then((r) => r.data);
