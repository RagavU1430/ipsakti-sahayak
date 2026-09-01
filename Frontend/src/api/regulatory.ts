import { request } from './client';
import type { AuthHeaders } from './client';
import type { RegulatoryAnalysisRequest, RegulatoryAnalysisResponse } from './types';

export function analyzeRegulatory(payload: RegulatoryAnalysisRequest, auth?: AuthHeaders) {
  return request<RegulatoryAnalysisResponse>('/api/v1/regulatory/analyze', {
    method: 'POST',
    body: JSON.stringify(payload),
  }, auth);
}
