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

export interface ApiRequestResult<T> {
  data: T;
  requestId: string | null;
  serverTiming: string | null;
  provider: string | null;
  chunks: number | null;
  route: string | null;
  backendTotalMs: number | null;
  tRequestStart: number;
  tResponseStart: number;
  tResponseEnd: number;
  httpStatus: number;
  responseSizeBytes: number;
}

export async function request<T>(path: string, options: RequestInit = {}, auth: AuthHeaders = {}): Promise<T> {
  return (await requestWithMeta<T>(path, options, auth)).data;
}

/** Returns non-sensitive timing and performance metadata exposed by the API. */
export async function requestWithMeta<T>(path: string, options: RequestInit = {}, auth: AuthHeaders = {}): Promise<ApiRequestResult<T>> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (!headers.has('X-Request-ID')) {
    headers.set('X-Request-ID', crypto.randomUUID());
  }
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (auth.token) {
    headers.set('Authorization', `Bearer ${auth.token}`);
  }
  if (auth.devUserId) {
    headers.set('X-Dev-User-Id', auth.devUserId);
  } else if (!auth.token) {
    headers.set('X-Dev-User-Id', 'demo-user');
  }

  const tRequestStart = performance.now();
  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, { ...options, headers });
  } catch {
    throw new ApiError(0, { code: 'NETWORK_ERROR', detail: 'IP-SAKTI could not reach the backend. Please check that the backend is running.' });
  }
  const tResponseStart = performance.now();

  if (response.status === 204) {
    const tResponseEnd = performance.now();
    const bt204 = response.headers.get('X-IPSAKTI-Backend-Total-Ms');
    return {
      data: undefined as T,
      requestId: response.headers.get('X-Request-ID'),
      serverTiming: response.headers.get('Server-Timing'),
      provider: response.headers.get('X-IPSAKTI-Provider'),
      chunks: null,
      route: response.headers.get('X-IPSAKTI-Route'),
      backendTotalMs: bt204 && /^[\d.]+$/.test(bt204) ? Number(bt204) : null,
      tRequestStart,
      tResponseStart,
      tResponseEnd,
      httpStatus: 204,
      responseSizeBytes: 0,
    };
  }

  const contentType = response.headers.get('content-type') || '';
  const textBody = await response.text();
  const tResponseEnd = performance.now();
  const responseSizeBytes = new Blob([textBody]).size;
  let body: any = {};
  if (contentType.includes('application/json') && textBody.trim()) {
    try {
      body = JSON.parse(textBody);
    } catch {
      body = {};
    }
  }

  if (!response.ok) {
    throw new ApiError(response.status, body);
  }

  const chunkText = response.headers.get('X-IPSAKTI-Chunks');
  const btText = response.headers.get('X-IPSAKTI-Backend-Total-Ms');
  return {
    data: body as T,
    requestId: response.headers.get('X-Request-ID'),
    serverTiming: response.headers.get('Server-Timing'),
    provider: response.headers.get('X-IPSAKTI-Provider'),
    chunks: chunkText && /^\d+$/.test(chunkText) ? Number(chunkText) : null,
    route: response.headers.get('X-IPSAKTI-Route'),
    backendTotalMs: btText && /^[\d.]+$/.test(btText) ? Number(btText) : null,
    tRequestStart,
    tResponseStart,
    tResponseEnd,
    httpStatus: response.status,
    responseSizeBytes,
  };
}

export function friendlyMessage(status: number): string {
  if (status === 0) return 'IP-SAKTI could not reach the backend. Please check your connection.';
  if (status === 400 || status === 422) return 'Please check the request details and try again.';
  if (status === 401 || status === 403) return 'Please sign in to continue.';
  if (status === 503) return 'IP-SAKTI could not process the request right now. Please try again.';
  if (status >= 500) return 'Something went wrong while processing the request.';
  return 'The request could not be completed.';
}
