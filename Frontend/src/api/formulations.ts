import { request } from './client';
import type { AuthHeaders } from './client';
import type {
  FormulationChatRequest,
  FormulationChatResponse,
  FormulationRequest,
  FormulationResponse,
  ProductReadinessResponse,
} from './types';

export function classifyFormulation(payload: FormulationRequest, auth?: AuthHeaders) {
  return request<FormulationResponse>('/api/v1/formulations/classify', {
    method: 'POST',
    body: JSON.stringify(payload),
  }, auth);
}

export function analyzeProductReadiness(payload: FormulationRequest, auth?: AuthHeaders) {
  return request<ProductReadinessResponse>('/api/v1/formulations/analyze', {
    method: 'POST',
    body: JSON.stringify(payload),
  }, auth);
}

export function chatFormulationReport(payload: FormulationChatRequest, auth?: AuthHeaders) {
  return request<FormulationChatResponse>('/api/v1/formulations/chat', {
    method: 'POST',
    body: JSON.stringify(payload),
  }, auth);
}

