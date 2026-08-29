export const ENDPOINTS = {
  auth: {
    login: '/auth/login',
    register: '/auth/register'
  },
  users: {
    me: '/users/me',
    updateMe: '/users/me'
  },
  documents: {
    list: '/documents',
    create: '/documents',
    byId: (id: number) => `/documents/${id}`,
    delete: (id: number) => `/documents/${id}`,
    file: (id: number) => `/documents/${id}/file`
  },
  tags: {
    list: '/collections',
    create: '/collections',
    byId: (id: number) => `/collections/${id}`,
    addDocument: (tagId: number, documentId: number) => `/collections/${tagId}/documents/${documentId}`,
    removeDocument: (tagId: number, documentId: number) => `/collections/${tagId}/documents/${documentId}`
  },
  profileMasters: {
    list: '/profile-masters',
    create: '/profile-masters',
    update: (id: number) => `/profile-masters/${id}`,
    delete: (id: number) => `/profile-masters/${id}`
  },
  sessions: {
    create: '/sessions',
    byId: (id: number) => `/sessions/${id}`,
    currentPage: (id: number) => `/sessions/${id}/current-page`,
    messages: (id: number) => `/sessions/${id}/messages`,
    voice: (id: number) => `/sessions/${id}/voice`,
    quiz: (id: number) => `/sessions/${id}/quiz`,
    quizAnswer: (id: number) => `/sessions/${id}/quiz/answer`,
    quizAttempts: (id: number) => `/sessions/${id}/quiz/attempts`,
    progress: (id: number) => `/sessions/${id}/progress`,
    notifications: (id: number) => `/sessions/${id}/notifications`,
    context: (id: number) => `/sessions/${id}/context`,
    reports: (id: number) => `/sessions/${id}/reports`,
    reportProgress: (id: number) => `/sessions/${id}/reports/progress`,
    reportQuiz: (id: number) => `/sessions/${id}/reports/quiz`,
    summary: (id: number) => `/sessions/${id}/summary`
  },
  reports: {
    file: (id: number) => `/reports/${id}/file`
  },
  voice: {
    capabilities: '/voice/capabilities'
  }
} as const;
