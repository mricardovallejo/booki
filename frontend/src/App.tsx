import { lazy, Suspense } from 'react';
import { Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import ProtectedRoute from './components/ProtectedRoute';
import { ROUTES } from './config/routes';

// Split the two heavy screens out of the initial bundle: SessionPage pulls in
// react-pdf / pdf.js, and AiProfilesPage is a large editor — neither is needed
// to render the login or library.
const SessionPage = lazy(() => import('./pages/SessionPage'));
const AiProfilesPage = lazy(() => import('./pages/AiProfilesPage'));
const ProfilePage = lazy(() => import('./pages/ProfilePage'));

function PageFallback() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center bg-booki-bg">
      <span className="h-6 w-6 animate-spin rounded-full border-2 border-white/20 border-t-booki-accent" />
    </div>
  );
}

function App() {
  return (
    <Suspense fallback={<PageFallback />}>
      <Routes>
        <Route path={ROUTES.login} element={<LoginPage />} />
        <Route
          path={ROUTES.home}
          element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route index element={<HomePage />} />
          <Route path="sessions/:sessionId" element={<SessionPage />} />
          <Route path="ai-profiles" element={<AiProfilesPage />} />
          <Route path="ai-profiles/:id" element={<AiProfilesPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>
      </Routes>
    </Suspense>
  );
}

export default App;
