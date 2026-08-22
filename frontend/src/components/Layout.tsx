import { useState } from 'react';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LANGUAGE_LABELS, getDefaultLanguage, setDefaultLanguage } from '../lib/preferences';
import { ROUTES } from '../config/routes';
import Logo from './Logo';
import ScrollToTop from './ScrollToTop';
import type { SessionLanguage } from '../types';

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const [defaultLanguage, setDefaultLanguageState] = useState<SessionLanguage>(getDefaultLanguage());

  const onLogout = () => {
    logout();
    navigate(ROUTES.login);
  };

  const onChangeDefaultLanguage = (lang: SessionLanguage) => {
    setDefaultLanguage(lang);
    setDefaultLanguageState(lang);
  };

  return (
    <div className="flex min-h-screen flex-col bg-booki-bg">
      <ScrollToTop />
      <header className="fixed top-0 z-50 w-full bg-gradient-to-b from-black/80 to-transparent px-6 py-4 backdrop-blur-sm">
        <div className="mx-auto flex max-w-7xl items-center justify-between">
          <Link to={ROUTES.home} className="flex items-center gap-2">
            <Logo />
          </Link>
          <nav className="hidden items-center gap-6 text-sm font-medium text-white/90 md:flex">
            <Link
              to={ROUTES.home}
              onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
              className="transition hover:text-booki-accent"
            >
              Home
            </Link>
            <Link to={ROUTES.library} className="transition hover:text-booki-accent">My books</Link>
            <Link to={ROUTES.tags} className="transition hover:text-booki-accent">Tags</Link>
            <Link to={ROUTES.masters} className="transition hover:text-booki-accent">Masters</Link>
          </nav>
          <div className="relative flex items-center gap-3">
            <button
              onClick={() => setMenuOpen((o) => !o)}
              className="flex items-center gap-2 rounded-full bg-booki-surface/80 px-4 py-1.5 text-sm font-medium text-white backdrop-blur transition hover:bg-booki-card"
            >
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-booki-accent text-xs font-bold uppercase">
                {(user?.name || user?.email || '?').slice(0, 1)}
              </span>
              {user?.name || 'My account'}
            </button>
            {menuOpen && (
              <div className="absolute right-0 top-full mt-2 w-64 rounded-lg bg-booki-surface py-2 shadow-2xl ring-1 ring-white/10">
                <p className="truncate px-4 py-1.5 text-xs text-booki-muted">{user?.email}</p>
                <div className="px-4 py-2">
                  <label className="mb-1 block text-[11px] font-medium uppercase tracking-wide text-white/50">
                    Default assistant language
                  </label>
                  <select
                    value={defaultLanguage}
                    onChange={(e) => onChangeDefaultLanguage(e.target.value as SessionLanguage)}
                    className="w-full rounded-md bg-booki-card px-2 py-1.5 text-sm text-white outline-none ring-1 ring-white/10 focus:ring-booki-accent"
                  >
                    {(Object.keys(LANGUAGE_LABELS) as SessionLanguage[]).map((lang) => (
                      <option key={lang} value={lang}>
                        {LANGUAGE_LABELS[lang]}
                      </option>
                    ))}
                  </select>
                </div>
                <Link
                  to={ROUTES.profile}
                  onClick={() => setMenuOpen(false)}
                  className="block px-4 py-2 text-sm text-white/90 transition hover:bg-booki-card"
                >
                  Edit profile
                </Link>
                <Link
                  to={ROUTES.masters}
                  onClick={() => setMenuOpen(false)}
                  className="block px-4 py-2 text-sm text-white/90 transition hover:bg-booki-card"
                >
                  Manage Masters
                </Link>
                <button
                  onClick={onLogout}
                  className="block w-full px-4 py-2 text-left text-sm text-white/90 transition hover:bg-booki-card"
                >
                  Sign out
                </button>
              </div>
            )}
          </div>
        </div>
      </header>
      <main className="flex-1 pt-16">
        <Outlet />
      </main>
    </div>
  );
}
