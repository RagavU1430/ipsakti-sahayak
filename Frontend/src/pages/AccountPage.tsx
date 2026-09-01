import type { Session } from '../api/auth';

export function AccountPage({ session, onLogout }: { session: Session; onLogout: () => void }) {
  return (
    <div className="page narrow-page">
      <section className="panel">
        <p className="eyebrow">Account</p>
        <h1>Session</h1>
        <dl className="account-list">
          <dt>Authentication mode</dt>
          <dd>{session.token ? 'Bearer token' : 'Development user id'}</dd>
          <dt>Identifier</dt>
          <dd>{session.devUserId || (session.token ? 'Token provided' : 'Not signed in')}</dd>
        </dl>
        <button className="button secondary danger" type="button" onClick={onLogout}>Logout</button>
      </section>
    </div>
  );
}
