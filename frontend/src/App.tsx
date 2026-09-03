import { Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import HomePage from './pages/HomePage';
import SessionPage from './pages/SessionPage';
import LoginPage from './pages/LoginPage';
import AiProfilesPage from './pages/AiProfilesPage';
import ProfilePage from './pages/ProfilePage';
import ProtectedRoute from './components/ProtectedRoute';
import { ROUTES } from './config/routes';

function App() {
  return (
    <Routes>
      <Route path={ROUTES.login} element={<LoginPage />} />
      <Route
        path="/"
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
  );
}

export default App;
