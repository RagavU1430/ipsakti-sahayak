import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request } from './client';

describe('api client', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('adds bearer and dev user headers without exposing backend internals to components', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ ok: true }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));

    await request('/api/v1/conversations', {}, { token: 'token-1', devUserId: 'dev-1' });

    const headers = fetchMock.mock.calls[0][1]?.headers as Headers;
    expect(headers.get('Authorization')).toBe('Bearer token-1');
    expect(headers.get('X-Dev-User-Id')).toBe('dev-1');
  });

  it('maps backend error bodies to friendly ApiError values', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      code: 'TRANSLATION_UNAVAILABLE',
      detail: 'Translation service is not configured for non-English requests.',
    }), {
      status: 503,
      headers: { 'Content-Type': 'application/json' },
    }));

    await expect(request('/api/v1/questions')).rejects.toMatchObject({
      status: 503,
      code: 'TRANSLATION_UNAVAILABLE',
      detail: 'Translation service is not configured for non-English requests.',
    } satisfies Partial<ApiError>);
  });
});
