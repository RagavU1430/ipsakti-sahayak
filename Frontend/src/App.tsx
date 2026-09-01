import { Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom';
import { useEffect, useMemo, useState } from 'react';
import { clearSession, isSignedIn, readSession, saveSession, type Session } from './api/auth';
import { AboutPage } from './pages/AboutPage';
import { AccountPage } from './pages/AccountPage';
import { AskPage } from './pages/AskPage';
import { ConversationDetailPage } from './pages/ConversationDetailPage';
import { FormulationPage } from './pages/FormulationPage';
import { HistoryPage } from './pages/HistoryPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { RegulatoryPage } from './pages/RegulatoryPage';

const navItems = [
  { to: '/ask', label: 'Ask IP' },
  { to: '/formulations', label: 'Formulation' },
  { to: '/regulatory', label: 'Regulatory' },
  { to: '/history', label: 'History' },
  { to: '/about', label: 'About' },
];

export function App() {
  const [session, setSession] = useState<Session>(() => readSession());
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const signedIn = isSignedIn(session);

  useEffect(() => setMenuOpen(false), [location.pathname]);

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
    <div className="app-shell">
      <header className="site-header">
        <NavLink className="brand" to="/" aria-label="IP-SAKTI Sahayak home">
          <span className="material-symbols-outlined">account_balance</span>
          <span>IP-SAKTI Sahayak</span>
        </NavLink>
        <button className="menu-button" type="button" onClick={() => setMenuOpen((open) => !open)} aria-expanded={menuOpen}>
          <span className="material-symbols-outlined">menu</span>
        </button>
        <nav className={menuOpen ? 'site-nav open' : 'site-nav'} aria-label="Primary navigation">
          {navItems.map((item) => (
            <NavLink key={item.to} to={item.to}>
              {item.label}
            </NavLink>
          ))}
          {signedIn ? <NavLink to="/account">Account</NavLink> : <NavLink className="button primary" style={{ minHeight: '36px', padding: '6px 16px' }} to="/login">Login</NavLink>}
        </nav>
      </header>

      <main>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/ask" element={<AskPage auth={auth} signedIn={signedIn} />} />
          <Route path="/formulations" element={<FormulationPage auth={auth} />} />
          <Route path="/regulatory" element={<RegulatoryPage auth={auth} />} />
          <Route path="/history" element={<Protected signedIn={signedIn}><HistoryPage auth={auth} /></Protected>} />
          <Route path="/history/:id" element={<Protected signedIn={signedIn}><ConversationDetailPage auth={auth} /></Protected>} />
          <Route path="/login" element={<LoginPage onLogin={handleLogin} signedIn={signedIn} />} />
          <Route path="/account" element={<Protected signedIn={signedIn}><AccountPage session={session} onLogout={handleLogout} /></Protected>} />
          <Route path="/about" element={<AboutPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>

      <footer className="site-footer">
        <div className="site-footer-content">
          <div>
            <div className="footer-brand">IP-SAKTI Sahayak</div>
            <p className="footer-copy">
              IP-SAKTI Sahayak provides evidence-backed information and is not a substitute for professional legal advice.
            </p>
          </div>
          <div className="footer-links">
            <NavLink to="/about">About</NavLink>
            <NavLink to="/ask">Ask IP</NavLink>
            <NavLink to="/regulatory">Regulatory</NavLink>
          </div>
        </div>
      </footer>
    </div>
  );
}

function Protected({ signedIn, children }: { signedIn: boolean; children: React.ReactNode }) {
  if (!signedIn) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}
