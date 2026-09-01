import { afterEach, describe, expect, it } from 'vitest';
import { clearSession, isSignedIn, readSession, saveSession } from './auth';

describe('auth session', () => {
  afterEach(() => clearSession());

  it('stores only session scoped auth material', () => {
    saveSession({ devUserId: 'demo-user' });

    expect(isSignedIn(readSession())).toBe(true);
    expect(readSession().devUserId).toBe('demo-user');
    expect(localStorage.getItem('ipSakti.devUserId')).toBeNull();
  });
});
