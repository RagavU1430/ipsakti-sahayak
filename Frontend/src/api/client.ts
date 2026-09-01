import type { ApiErrorBody } from './types';

const baseUrl = (import.meta.env.VITE_BACKEND_BASE_URL || '').replace(/\/$/, '');

export class ApiError extends Error {
  status: number;
  code: string;
  detail: string;

  constructor(status: number, body: ApiErrorBody = {}) {
    const detail = body.detail || body.message || body.error || friendlyMessage(status);
    super(detail);
    this.status = status;
    this.code = body.code || 'REQUEST_FAILED';
    this.detail = detail;
  }
}

export interface AuthHeaders {
  token?: string;
  devUserId?: string;
}

export async function request<T>(path: string, options: RequestInit = {}, auth: AuthHeaders = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (auth.token) {
    headers.set('Authorization', `Bearer ${auth.token}`);
  }
  if (auth.devUserId) {
    headers.set('X-Dev-User-Id', auth.devUserId);
  }

  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, { ...options, headers });
  } catch {
    throw new ApiError(0, { code: 'NETWORK_ERROR', detail: 'IP-SAKTI could not reach the backend. Please check that the backend is running.' });
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('application/json') ? await response.json() : {};

  if (!response.ok) {
    throw new ApiError(response.status, body);
  }

  return body as T;
}

export function friendlyMessage(status: number): string {
  if (status === 0) return 'IP-SAKTI could not reach the backend. Please check your connection.';
  if (status === 400 || status === 422) return 'Please check the request details and try again.';
  if (status === 401 || status === 403) return 'Please sign in to continue.';
  if (status === 503) return 'IP-SAKTI could not process the request right now. Please try again.';
  if (status >= 500) return 'Something went wrong while processing the request.';
  return 'The request could not be completed.';
}
