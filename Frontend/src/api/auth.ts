const tokenKey = 'ipSakti.sessionToken';
const devUserKey = 'ipSakti.devUserId';

export interface Session {
  token?: string;
  devUserId?: string;
}

export function readSession(): Session {
  return {
    token: sessionStorage.getItem(tokenKey) || undefined,
    devUserId: sessionStorage.getItem(devUserKey) || undefined,
  };
}

export function saveSession(session: Session) {
  sessionStorage.removeItem(tokenKey);
  sessionStorage.removeItem(devUserKey);
  if (session.token) sessionStorage.setItem(tokenKey, session.token);
  if (session.devUserId) sessionStorage.setItem(devUserKey, session.devUserId);
}

export function clearSession() {
  sessionStorage.removeItem(tokenKey);
  sessionStorage.removeItem(devUserKey);
}

export function isSignedIn(session: Session) {
  return Boolean(session.token || session.devUserId);
}
