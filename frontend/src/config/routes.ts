export const ROUTES = {
  login: '/login',
  home: '/',
  library: '/#library',
  tags: '/#tags',
  masters: '/masters',
  profile: '/profile',
  session: (sessionId: number | string) => `/sessions/${sessionId}`
} as const;
