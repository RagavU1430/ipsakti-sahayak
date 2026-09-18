import { request, requestWithMeta } from './client';
import type { AuthHeaders } from './client';
import type { QuestionRequest, QuestionResponse } from './types';

export function askQuestion(payload: QuestionRequest, auth?: AuthHeaders) {
  return request<QuestionResponse>('/api/v1/questions', {
    method: 'POST',
    body: JSON.stringify(payload),
  }, auth);
}

export function askQuestionWithMeta(payload: QuestionRequest, auth?: AuthHeaders, requestId?: string) {
  return requestWithMeta<QuestionResponse>('/api/v1/questions', {
    method: 'POST', headers: requestId ? { 'X-Request-ID': requestId } : undefined, body: JSON.stringify(payload),
  }, auth);
}
