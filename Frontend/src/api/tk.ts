import { request } from './client';
import type { AuthHeaders } from './client';
import type { TkOverlapRequest, TkOverlapResponse } from './types';

export function analyzeTkOverlap(payload: TkOverlapRequest, auth?: AuthHeaders) {
  return request<TkOverlapResponse>('/api/v1/tk/overlap', {
    method: 'POST',
    body: JSON.stringify(payload),
  }, auth);
}
