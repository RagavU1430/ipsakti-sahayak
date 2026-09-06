import { FormEvent, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import type { Session } from '../api/auth';
import logoImg from '../assets/logo.png';

export function LoginPage({ onLogin, signedIn }: { onLogin: (session: Session) => void; signedIn: boolean }) {
  const [mode, setMode] = useState<'dev' | 'token'>('dev');
  const [value, setValue] = useState('demo-user');
  const navigate = useNavigate();

  if (signedIn) {
    return <Navigate to="/account" replace />;
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const trimmed = value.trim();
    if (!trimmed) return;
    onLogin(mode === 'dev' ? { devUserId: trimmed } : { token: trimmed });
    navigate('/account');
  }

  return (
    <div className="page auth-page">
      <section className="panel auth-card">
        <div style={{ display: 'flex', alignItems: 'center', gap: '14px', marginBottom: '16px' }}>
          <img
            src={logoImg}
            alt="IP-SAKTI Sahayak Logo"
            style={{
              width: '52px',
              height: '52px',
              objectFit: 'contain',
              borderRadius: '50%',
              background: '#ffffff',
              padding: '2px',
              boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
              border: '1px solid var(--outline-variant)'
            }}
          />
          <div>
            <p className="eyebrow" style={{ margin: 0 }}>Authentication</p>
            <h1 style={{ margin: 0, fontSize: '1.45rem' }}>Sign in to save conversations</h1>
          </div>
        </div>
        <p>Use an issued bearer token, or a development user id when the backend is running in dev mode.</p>
        <form onSubmit={submit} className="form-grid">
          <label className="field">
            <span>Session type</span>
            <select value={mode} onChange={(event) => setMode(event.target.value as 'dev' | 'token')}>
              <option value="dev">Development user id</option>
              <option value="token">Bearer token</option>
            </select>
          </label>
          <label className="field">
            <span>{mode === 'dev' ? 'Development user id' : 'Bearer token'}</span>
            <input
              value={value}
              onChange={(event) => setValue(event.target.value)}
              placeholder={mode === 'dev' ? 'demo-user' : 'Paste token from backend auth provider'}
              type={mode === 'token' ? 'password' : 'text'}
            />
          </label>
          <button className="button primary" type="submit">Login</button>
        </form>
        <p className="muted small">Session details are stored only in browser session storage and are cleared when you log out or close the tab.</p>
      </section>
    </div>
  );
}
