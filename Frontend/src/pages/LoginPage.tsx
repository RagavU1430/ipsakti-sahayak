import { FormEvent, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import type { Session } from '../api/auth';

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
        <p className="eyebrow">Authentication</p>
        <h1>Sign in to save conversations</h1>
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
