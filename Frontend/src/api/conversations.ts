import { request } from './client';
import type { AuthHeaders } from './client';
import type { ConversationDetail, ConversationMessageResponse, ConversationPage } from './types';

export function listConversations(auth: AuthHeaders) {
  return request<ConversationPage>('/api/v1/conversations?page=0&size=20', {}, auth);
}

export function createConversation(title: string, auth: AuthHeaders) {
  return request<{ id: string; title: string; created_at: string; updated_at: string }>('/api/v1/conversations', {
    method: 'POST',
    body: JSON.stringify({ title }),
  }, auth);
}

export function getConversation(id: string, auth: AuthHeaders) {
  return request<ConversationDetail>(`/api/v1/conversations/${id}`, {}, auth);
}

export function askInConversation(id: string, question: string, jurisdiction: string, language: string, auth: AuthHeaders) {
  return request<ConversationMessageResponse>(`/api/v1/conversations/${id}/messages`, {
    method: 'POST',
    body: JSON.stringify({ question, jurisdiction, language }),
  }, auth);
}

export function deleteConversation(id: string, auth: AuthHeaders) {
  return request<void>(`/api/v1/conversations/${id}`, { method: 'DELETE' }, auth);
}
