import { request } from './client';
import type { AuthHeaders } from './client';
import type { QuestionRequest, QuestionResponse } from './types';

export function askQuestion(payload: QuestionRequest, auth?: AuthHeaders) {
  return request<QuestionResponse>('/api/v1/questions', {
    method: 'POST',
    body: JSON.stringify(payload),
  }, auth);
}
