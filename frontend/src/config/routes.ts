export const ROUTES = {
  login: '/login',
  home: '/',
  library: '/#library',
  tags: '/#tags',
  aiProfiles: '/ai-profiles',
  aiProfile: (id: number | string) => `/ai-profiles/${id}`,
  profile: '/profile',
  session: (sessionId: number | string) => `/sessions/${sessionId}`
} as const;
