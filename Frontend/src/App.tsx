import { Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom';
import React, { useEffect, useMemo, useState, Suspense, lazy } from 'react';
import { clearSession, isSignedIn, readSession, saveSession, type Session } from './api/auth';
import { AboutPage } from './pages/AboutPage';
import { AccountPage } from './pages/AccountPage';
import { AskPage } from './pages/AskPage';
import { ConversationDetailPage } from './pages/ConversationDetailPage';
const FormulationPage = lazy(() => import("./pages/FormulationPage"));
import { HistoryPage } from './pages/HistoryPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { RegulatoryPage } from './pages/RegulatoryPage';
import { TkOverlapPage } from './pages/TkOverlapPage';
const VoiceChatOverlay = lazy(() => import("./components/VoiceChatOverlay"));
import { FeedbackModal } from './components/FeedbackModal';
import { KeyboardHelpOverlay } from './components/KeyboardHelpOverlay';

import { useTheme } from './hooks/useTheme';
import { ErrorBoundary } from './components/ErrorBoundary';

const navItems = [
  { to: '/ask', label: 'Ask Query', icon: 'add' },
  { to: '/tk', label: 'TK Overlap', icon: 'eco' },
  { to: '/formulations', label: 'Formulation', icon: 'science' },
  { to: '/regulatory', label: 'Regulatory', icon: 'policy' },
  { to: '/history', label: 'History', icon: 'history' },
  { to: '/about', label: 'About', icon: 'info' },
];

export function App() {
  const [session, setSession] = useState<Session>(() => readSession());
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(() => {
    try {
      return localStorage.getItem('ipsakti_sidebar_collapsed') === 'true';
    } catch {
      return false;
    }
  });
  const [menuOpen, setMenuOpen] = useState(false);
  const [isGlobalVoiceOpen, setIsGlobalVoiceOpen] = useState(false);
  const [isFeedbackOpen, setIsFeedbackOpen] = useState(false);
  const [showKeyboardHelp, setShowKeyboardHelp] = useState(false);
  const location = useLocation();
  const signedIn = isSignedIn(session);
  const { theme, resolvedTheme, toggleTheme } = useTheme();

  useEffect(() => setMenuOpen(false), [location.pathname]);

  const toggleSidebar = () => {
    setSidebarCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem('ipsakti_sidebar_collapsed', String(next));
      } catch {}
      return next;
    });
  };

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      const isInput = target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable);
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'b' && !isInput) {
        e.preventDefault();
        toggleSidebar();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const auth = useMemo(() => ({ token: session.token, devUserId: session.devUserId }), [session]);

  function handleLogin(nextSession: Session) {
    saveSession(nextSession);
    setSession(nextSession);
  }

  function handleLogout() {
    clearSession();
    setSession({});
  }

  return (
    <div className="app-shell nyaya-shell">
      <aside
        className={`gov-sidebar ${sidebarCollapsed ? 'collapsed' : ''}`}
        aria-label="Service navigation"
        aria-hidden={sidebarCollapsed}
      >
        <div className="gov-sidebar-inner">
          <div className="sidebar-header-row">
            <NavLink className="new-query-button" to="/ask">
              <span className="material-symbols-outlined ayurvedic-logo" aria-hidden="true">spa</span>
              <span>IP-SAKTI Sahayak</span>
            </NavLink>
            <button
              type="button"
              className="sidebar-toggle-btn inside-sidebar"
              onClick={toggleSidebar}
              title="Collapse sidebar (Ctrl+B)"
              aria-label="Collapse sidebar"
            >
              <span className="material-symbols-outlined">dock_to_left</span>
            </button>
          </div>

          <button className="menu-button sidebar-menu-button" type="button" onClick={() => setMenuOpen((open) => !open)} aria-expanded={menuOpen}>
            <span className="material-symbols-outlined">menu</span>
            Menu
          </button>

          <nav className={menuOpen ? 'portal-nav open' : 'portal-nav'} aria-label="Primary navigation">
            {navItems.map((item) => (
              <NavLink key={item.to} to={item.to}>
                <span className="material-symbols-outlined">{item.icon}</span>
                {item.label}
              </NavLink>
            ))}
            {signedIn ? (
              <NavLink to="/account">
                <span className="material-symbols-outlined">account_circle</span>
                Account
              </NavLink>
            ) : (
              <NavLink to="/login">
                <span className="material-symbols-outlined">login</span>
                Login
              </NavLink>
            )}
          </nav>

          <button type="button" className="feedback-pill" onClick={() => setIsFeedbackOpen(true)} title="Send feedback or suggestions">
            Feedback ★
          </button>
        </div>
      </aside>

      <section className="portal-workspace">
        <header className="portal-topbar">
          <div className="portal-topbar-left">
            <button
              type="button"
              className={`sidebar-toggle-btn topbar-toggle ${sidebarCollapsed ? 'collapsed-state' : ''}`}
              onClick={toggleSidebar}
              title={sidebarCollapsed ? 'Expand sidebar (Ctrl+B)' : 'Collapse sidebar (Ctrl+B)'}
              aria-label={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
              aria-expanded={!sidebarCollapsed}
            >
              <span className="material-symbols-outlined">
                {sidebarCollapsed ? 'dock_to_left' : 'dock_to_left'}
              </span>
            </button>
            <NavLink className="portal-brand" to="/" aria-label="IP-SAKTI Sahayak home">
              <span className="material-symbols-outlined">account_balance</span>
              <span>IP-SAKTI Sahayak</span>
            </NavLink>
          </div>
          <div className="topbar-actions">
            <button
              type="button"
              className={`theme-toggle-btn ${resolvedTheme === 'dark' ? 'dark' : ''}`}
              onClick={toggleTheme}
              title={`Toggle theme (${theme})`}
              aria-label={`Current theme is ${theme}. Click to change.`}
              style={{
                background: 'transparent',
                border: '1px solid var(--outline-variant)',
                borderRadius: '50%',
                width: '36px',
                height: '36px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer',
                color: 'var(--on-surface-variant)',
                transition: 'all 0.2s ease',
              }}
            >
              <span className="material-symbols-outlined" style={{ fontSize: '20px' }}>
                {resolvedTheme === 'dark' ? 'dark_mode' : 'light_mode'}
              </span>
            </button>
            <button
              type="button"
              className="keyboard-shortcut-btn"
              onClick={() => setShowKeyboardHelp(true)}
              title="Keyboard shortcuts (?)"
              aria-label="Show keyboard shortcuts"
              style={{
                background: 'transparent',
                border: '1px solid var(--outline-variant)',
                borderRadius: '50%',
                width: '36px',
                height: '36px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer',
                color: 'var(--on-surface-variant)',
                transition: 'all 0.2s ease',
              }}
            >
              <span className="material-symbols-outlined" style={{ fontSize: '20px' }}>keyboard</span>
            </button>
            <button
              type="button"
              className="live-voice-launch-btn compact"
              onClick={() => setIsGlobalVoiceOpen(true)}
              title="Launch Live Conversational Voice Assistant"
            >
              <span className="live-voice-pulse-dot" />
              <span className="material-symbols-outlined">graphic_eq</span>
              <span>Live Voice</span>
            </button>
            <NavLink className="home-pill" to="/">Home</NavLink>
          </div>
        </header>

        <Suspense fallback={<div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-dim)' }}>Loading component...</div>}>
          <VoiceChatOverlay
            isOpen={isGlobalVoiceOpen}
            onClose={() => setIsGlobalVoiceOpen(false)}
            auth={auth}
          />
        </Suspense>

        <KeyboardHelpOverlay
          isOpen={showKeyboardHelp}
          onClose={() => setShowKeyboardHelp(false)}
        />

        <FeedbackModal
          isOpen={isFeedbackOpen}
          onClose={() => setIsFeedbackOpen(false)}
        />

        <main className="portal-main">
          <ErrorBoundary>
            <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/ask" element={<AskPage auth={auth} signedIn={signedIn} />} />
            <Route path="/tk" element={<TkOverlapPage auth={auth} />} />
            <Route path="/formulations" element={<FormulationPage auth={auth} />} />
            <Route path="/formulation" element={<FormulationPage auth={auth} />} />
            <Route path="/regulatory" element={<RegulatoryPage auth={auth} />} />
            <Route path="/history" element={<Protected signedIn={signedIn}><HistoryPage auth={auth} /></Protected>} />
            <Route path="/history/:id" element={<Protected signedIn={signedIn}><ConversationDetailPage auth={auth} /></Protected>} />
            <Route path="/login" element={<LoginPage onLogin={handleLogin} signedIn={signedIn} />} />
            <Route path="/account" element={<Protected signedIn={signedIn}><AccountPage session={session} onLogout={handleLogout} /></Protected>} />
            <Route path="/about" element={<AboutPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </ErrorBoundary>
        </main>
      </section>
    </div>
  );
}

function Protected({ signedIn, children }: { signedIn: boolean; children: React.ReactNode }) {
  if (!signedIn) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}
